package com.abhedyam.service;

import com.abhedyam.model.FcmDetails;
import com.abhedyam.model.enums.AppLanguage;
import com.abhedyam.model.Owner;
import com.abhedyam.model.OwnerSession;
import com.abhedyam.repository.FcmDetailsRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSessionRepository;
import com.abhedyam.service.interfaces.IFcmService;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService implements IFcmService {
    
    private final FcmDetailsRepository fcmDetailsRepository;
    private final OwnerRepository ownerRepository;
    private final OwnerSessionRepository sessionRepository;
    
    @Transactional
    public FcmDetails registerToken(UUID userId, String token, String packageName, String deviceId, String deviceType, AppLanguage language) {
        Optional<FcmDetails> existing = fcmDetailsRepository.findByUserIdAndTokenAndPackageName(userId, token, packageName);
        
        if (existing.isPresent()) {
            FcmDetails fcmDetails = existing.get();
            if (deviceId != null) fcmDetails.setDeviceId(deviceId);
            if (deviceType != null) fcmDetails.setDeviceType(deviceType);
            if (language != null) fcmDetails.setLanguage(language);
            return fcmDetailsRepository.save(fcmDetails);
        }
        
        FcmDetails fcmDetails = new FcmDetails();
        fcmDetails.setUserId(userId);
        fcmDetails.setToken(token);
        fcmDetails.setPackageName(packageName);
        fcmDetails.setDeviceId(deviceId);
        fcmDetails.setDeviceType(deviceType);
        fcmDetails.setLanguage(language);
        
        return fcmDetailsRepository.save(fcmDetails);
    }
    
    @Transactional
    public void unregisterToken(UUID userId, String token, String packageName) {
        fcmDetailsRepository.deleteByUserIdAndTokenAndPackageName(userId, token, packageName);
    }
    
    @Override
    public int sendNotificationToUser(UUID userId, java.util.function.Function<String, String[]> textIn, String packageName,
                                      java.util.Map<String, String> data) {
        List<FcmDetails> fcmDetailsList = fcmDetailsRepository.findByUserIdAndPackageName(userId, packageName);
        
        if (fcmDetailsList.isEmpty()) {
            log.warn("No FCM tokens found for user {} with package {}", userId, packageName);
            return 0;
        }
        
        int sent = 0;
        for (FcmDetails fcmDetails : fcmDetailsList) {
            // The phone's app language (sent with its token); older apps sent none = English.
            String[] text = textIn.apply(fcmDetails.getLanguage() == null ? "en" : fcmDetails.getLanguage().code());
            if (send(userId, fcmDetails, text[0], text[1], data)) sent++;
        }
        return sent;
    }

    /** One push to one phone. A dead token (uninstalled, new token) is forgotten, or every push keeps trying it. */
    private boolean send(UUID userId, FcmDetails fcmDetails, String title, String body, Map<String, String> data) {
        try {
            Message message = Message.builder()
                .setToken(fcmDetails.getToken())
                .setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build())
                .putData("type", "reminder")
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                        .setSound("default")
                        .setChannelId(channelFor(data))
                        .build())
                    .build())
                .setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                        .setSound("default")
                        .setBadge(1)
                        .build())
                    .build())
                .build();
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM notification sent successfully to user {}: {}", userId, response);
            return true;
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM notification to user {} with token {}: {}", userId, fcmDetails.getToken(), e.getMessage());
            var code = e.getMessagingErrorCode();
            if (code == com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED
                || code == com.google.firebase.messaging.MessagingErrorCode.INVALID_ARGUMENT) {
                log.info("Removing invalid token for user {}: {}", userId, code);
                fcmDetailsRepository.delete(fcmDetails);
            }
        } catch (Exception e) {
            log.error("Unexpected error sending FCM notification to user {}: {}", userId, e.getMessage(), e);
        }
        return false;
    }

    /**
     * Business plan, several shops: a shop's push also reaches the owner's own phones that are open in another of their
     * shops (each phone registers its token under the shop it is in). The title starts with the shop's name and the data
     * carries {@code shopId} + {@code shopName}, so tapping it switches to that shop first. Helpers' phones are left out:
     * they work for one shop only. Paused shops are skipped (nobody is signed in to them).
     */
    @Override
    public int sendToOtherShops(UUID shopId, java.util.function.Function<String, String[]> textIn, Map<String, String> data) {
        Owner shop = ownerRepository.findById(shopId).orElse(null);
        if (shop == null) return 0;
        Map<String, String> withShop = new java.util.HashMap<>(data);
        withShop.put("shopId", shopId.toString());
        withShop.put("shopName", shop.getBusinessName());
        int sent = 0;
        for (FcmDetails d : otherShopPhones(shop)) {
            String[] text = textIn.apply(d.getLanguage() == null ? "en" : d.getLanguage().code());
            if (send(d.getUserId(), d, shop.getBusinessName() + " · " + text[0], text[1], withShop)) sent++;
        }
        return sent;
    }

    /** The owner's phones (not helpers') signed in to the other open shops of [shop]'s Gmail. */
    List<FcmDetails> otherShopPhones(Owner shop) {
        Owner main = ownerRepository.main(shop);
        List<Owner> shops = new ArrayList<>();
        shops.add(main);
        shops.addAll(ownerRepository.findByParentOwnerIdOrderByCreatedAtAsc(main.getId()));
        List<FcmDetails> phones = new ArrayList<>();
        if (shops.size() < 2) return phones; // one shop: the common case, nothing more to look up
        for (Owner o : shops) {
            if (o.getId().equals(shop.getId()) || !Boolean.TRUE.equals(o.getIsActive()) || ownerRepository.paused(o)) continue;
            Set<String> ownerPhones = sessionRepository.findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(o.getId()).stream()
                    .filter(x -> x.getMemberId() == null && x.getDeviceId() != null)
                    .map(OwnerSession::getDeviceId)
                    .collect(Collectors.toSet());
            if (ownerPhones.isEmpty()) continue;
            for (FcmDetails d : fcmDetailsRepository.findByUserIdAndPackageName(o.getId(), com.abhedyam.util.PackageConstants.BUSINESS_APP_PACKAGE)) {
                if (ownerPhones.contains(d.getDeviceId())) phones.add(d);
            }
        }
        return phones;
    }

    /**
     * Silent "something changed" ping to the owner's other phones (data only: nothing is shown, apps before 1.13 ignore it).
     * The open app reloads its lists. [exceptDeviceId]: the phone that made the change.
     */
    public void sendSync(UUID userId, String exceptDeviceId) {
        for (FcmDetails d : fcmDetailsRepository.findByUserIdAndPackageName(userId, com.abhedyam.util.PackageConstants.BUSINESS_APP_PACKAGE)) {
            if (exceptDeviceId != null && exceptDeviceId.equals(d.getDeviceId())) continue;
            try {
                FirebaseMessaging.getInstance().send(Message.builder()
                        .setToken(d.getToken())
                        .putData("type", "sync")
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.NORMAL)
                                .setCollapseKey("sync")
                                .setTtl(60_000)
                                .build())
                        .build());
            } catch (FirebaseMessagingException e) {
                var code = e.getMessagingErrorCode();
                if (code == com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED) fcmDetailsRepository.delete(d);
                else log.warn("Sync ping failed for user {}: {}", userId, e.getMessage());
            } catch (Exception e) {
                log.warn("Sync ping failed for user {}: {}", userId, e.getMessage());
            }
        }
    }

    /** Android channel per kind, so the owner can silence one kind in phone settings. The app creates these channels;
     *  a phone on an older build falls back to the manifest default ("reminders"). */
    static String channelFor(java.util.Map<String, String> data) {
        String category = data.getOrDefault("category", "");
        String entity = data.getOrDefault("relatedEntityType", "");
        if (category.equals("PAYMENT_NEEDS_OK")) return "payments";
        if (category.equals("DUE_DIGEST")) return "summary";
        if (entity.equals("DUE") || entity.equals("VISIT")) return "dues";
        return "reminders";
    }
}
