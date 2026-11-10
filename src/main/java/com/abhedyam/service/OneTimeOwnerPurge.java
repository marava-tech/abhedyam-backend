package com.abhedyam.service;

import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.service.interfaces.IAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * One-time cleanup (2026-11-11, owner's decision): erase abandoned test sign-ups (joined over 7 days ago, 0-2 customers)
 * so they count as fresh users if they return. Uses the normal deleteAccount (data, uploaded files, Firebase login).
 * Each id is re-checked at run time; already-deleted ids are skipped, so restarts do nothing. Delete this file after it ran.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneTimeOwnerPurge implements ApplicationRunner {

    private static final List<String> IDS = List.of(
            "995cf41c-e66c-44d3-8013-c4a414a2ce7e",
            "757e46f8-9913-415c-b30b-8f30e601bae1",
            "850e1b11-26c2-4a93-85c4-4c12f589b370",
            "b7986173-3969-4fcc-be0c-6cbe063ea405",
            "7eba2e08-ebfd-498a-9cec-1975645ad58e",
            "1814891d-3c18-41f5-a8bb-18730ac0a3b7",
            "d3445b4e-0e81-4d66-9150-468d95c96389",
            "1c949cf6-28d4-45e9-8aa0-d3e683bf5d50",
            "a1f0ee66-e6c3-4d00-a19c-b8ab8bd73b5e",
            "a7357004-5c9d-4a27-bba3-0f45175e134f",
            "28ecb02f-14f4-4dd7-868d-d9c19bd24739",
            "b48e5ee7-e061-4548-8b02-c8d0191e28a4",
            "35d82cbd-d043-4f19-8e36-5b51b4ac5e30",
            "c73eb2fe-707f-4521-9ead-46fbc7ac45ec",
            "945784fb-b0ea-4f3c-b861-a77eaf9df233",
            "d60e4d84-c59e-4a6d-9e87-7c68b7efe044",
            "27316692-7fb8-4c96-969f-619da5448c27",
            "6fb9d190-2631-4623-86b3-28eab1aa4d3d",
            "5e245328-4866-44f1-af3a-fc2a45626609",
            "aceb4532-e1cf-4224-b127-e56a21cd5afe",
            "412f56a7-ae0b-4964-b92a-a0a1175380e7",
            "53f23ec3-3680-4ade-9f20-5443bcd803b7",
            "280db7da-3319-400f-b50a-cd0d465dfd6c",
            "284b8828-6c3c-42e1-856a-3b86e26b060d",
            "397ca5df-9d68-45a2-84b0-8f6ec57f91db",
            "3242fee4-f84c-47b2-b578-615754457d5c",
            "c7e63181-c624-4107-9fdd-74c3538beb3a",
            "5504066c-f114-445d-b640-9f6be6c01e25",
            "81ff6207-3160-46f6-937a-58ed52751926",
            "aa09393d-8970-4742-9285-4703fbf9948c",
            "baee0ccd-3dd6-457e-8454-43e64453a427",
            "f7b4bd9a-b76b-42da-84e0-eb8cbaefdc69",
            "96394207-35f2-4902-9cef-206ac62c805e",
            "c5af494a-47c9-44a7-aedd-0a619fd6803b",
            "b5a8cfe2-fa7f-4218-bede-acd2e086b4ce",
            "f26c770d-a6b1-4eba-a265-ef598795dda3",
            "0aaab2d8-e2ae-4036-b208-f7a6d07d3f31",
            "0ca8fa1c-85d7-4380-b23f-79abeb07ccfe",
            "94ea7f07-d1d2-4081-aa3f-8e66d9d187d5",
            "e0ec7e22-2341-422d-8644-cd2a92bf8f06",
            "446208dd-31c4-4abc-89f5-5dd262072702",
            "9c01bf83-8f38-4aa5-8f3c-5093b8aba8a9",
            "fe57c71f-5e01-4710-ab77-520dd291a9d6",
            "08ed499d-eeb4-45f1-9a32-6b18e2d2b770",
            "644491a5-9e8e-4c8d-9685-7edfa2f477ff",
            "6a99b91f-0d4c-4d00-856b-d87254873bfb",
            "101f7691-803c-4fe9-9b16-b1727b8b1ac6",
            "a9751862-4705-4f80-b49f-66bbe9a8e807",
            "db848410-02ec-42c4-bc56-5782de3fde0d",
            "17847f16-d066-46d0-a5e6-bd4cdf6a0153",
            "5272b7b7-af93-4e8e-b07e-84748f515ac9",
            "ff9c194a-778c-49b2-a2fb-a43ddf2ac37d",
            "3d9fb41d-6c6e-4b33-8e09-fae6609db6ca",
            "f849a8d4-7ea8-4d38-893f-71389da9726b",
            "2792b855-0c1b-4b46-a988-ca0676c38847",
            "6b9568e3-9fb8-4ce8-8399-8a985678829b",
            "d9ad3049-d364-49e4-a0c9-5e2fbf3a3e3d");

    private final OwnerRepository owners;
    private final CustomerRepository customers;
    private final IAccountService accounts;

    @Override
    public void run(ApplicationArguments args) {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = 0;
        for (String raw : IDS) {
            UUID id = UUID.fromString(raw);
            try {
                var owner = owners.findById(id).orElse(null);
                if (owner == null) continue;
                if (owner.getCreatedAt() == null || owner.getCreatedAt().isAfter(cutoff)
                        || customers.findByOwnerId(id).size() > 2) {
                    log.warn("One-time purge skipped {}: no longer matches the rule", id);
                    continue;
                }
                accounts.deleteAccount(id);
                deleted++;
            } catch (Exception e) {
                log.error("One-time purge failed for {}: {}", id, e.getMessage(), e);
            }
        }
        log.info("One-time purge finished: {} owners deleted", deleted);
    }
}
