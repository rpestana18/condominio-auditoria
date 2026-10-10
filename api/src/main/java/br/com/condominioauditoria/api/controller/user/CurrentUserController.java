package br.com.condominioauditoria.api.controller.user;

import br.com.condominioauditoria.api.dto.response.user.CurrentUserResponse;
import br.com.condominioauditoria.api.service.user.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who is logged in, with which roles and in which condominiums. */
@RestController
@RequestMapping("/api/me")
class CurrentUserController {

    private final CurrentUserService currentUser;

    CurrentUserController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping
    CurrentUserResponse me() {
        return currentUser.me();
    }
}
