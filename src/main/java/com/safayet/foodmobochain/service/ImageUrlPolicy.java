package com.safayet.foodmobochain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Restricts seller-submitted image paths to bundled assets or this deployment's Cloudinary account. */
@Component
public class ImageUrlPolicy {
    private final String cloudName;
    public ImageUrlPolicy(@Value("${cloudinary.cloud-name:}") String cloudName) { this.cloudName = cloudName; }

    public void requireTrusted(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.length() > 512) {
            throw new IllegalArgumentException("Select an image or upload a new one");
        }
        boolean local = imageUrl.matches("/images/[a-zA-Z0-9_./-]+\\.(webp|png|jpg|jpeg|svg)")
                && !imageUrl.contains("..") && !imageUrl.contains("//");
        boolean hosted = !cloudName.isBlank() && imageUrl.startsWith(
                "https://res.cloudinary.com/" + cloudName + "/image/upload/")
                && !imageUrl.contains("\n") && !imageUrl.contains("\r");
        if (!local && !hosted) throw new IllegalArgumentException("Image must be an approved local asset or Cloudinary upload");
    }
}
