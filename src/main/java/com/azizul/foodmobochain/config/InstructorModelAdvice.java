package com.azizul.foodmobochain.config;

import com.azizul.foodmobochain.service.InstructorAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Show a small role-switch entry only in verified instructor browser sessions. */
@ControllerAdvice
@RequiredArgsConstructor
public class InstructorModelAdvice {
    private final InstructorAccessService access;
    @ModelAttribute("instructorAccessAvailable")
    public boolean instructorAccessAvailable() {
        return access.enabledForStaging();
    }

    @ModelAttribute("instructorMode")
    public boolean instructorMode(HttpServletRequest request) {
        return request.getAttribute(InstructorAccessService.REQUEST_ATTRIBUTE)
                instanceof InstructorAccessService.InstructorSession;
    }
}
