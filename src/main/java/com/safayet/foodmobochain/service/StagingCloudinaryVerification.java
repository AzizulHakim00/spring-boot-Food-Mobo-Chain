package com.safayet.foodmobochain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/** Temporary, opt-in check of a real Cloudinary upload on the staging service. */
@Component
@ConditionalOnProperty(name = "cloudinary.verify-once", havingValue = "true")
public class StagingCloudinaryVerification {
    private static final Logger log = LoggerFactory.getLogger(StagingCloudinaryVerification.class);
    private final CloudinaryImageService imageService;
    private final String renderServiceId;

    public StagingCloudinaryVerification(CloudinaryImageService imageService,
                                         @Value("${RENDER_SERVICE_ID:}") String renderServiceId) {
        this.imageService = imageService;
        this.renderServiceId = renderServiceId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (!"srv-db4h57vlk1mc73816qk0".equals(renderServiceId)) {
            log.warn("CLOUDINARY_VERIFY skipped outside the Food Mobo Chain staging service");
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                byte[] image;
                try (InputStream source = new ClassPathResource("cloudinary-verification.jpg").getInputStream()) {
                    image = source.readAllBytes();
                }
                String url = imageService.uploadBytes(image, "foods");
                log.info("CLOUDINARY_VERIFY success url={}", url);
            } catch (Exception error) {
                log.error("CLOUDINARY_VERIFY failed type={} message={}",
                        error.getClass().getSimpleName(), error.getMessage());
            }
        });
    }
}
