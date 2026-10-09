package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.service.CloudinaryImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SellerImageController {
    private final CloudinaryImageService imageService;

    @PostMapping("/seller/uploads/images")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "foods") String kind) {
        if (!kind.equals("foods") && !kind.equals("carts")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported seller image kind");
        }
        try {
            return Map.of("url", imageService.upload(file, kind));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image hosting unavailable");
        }
    }
}
