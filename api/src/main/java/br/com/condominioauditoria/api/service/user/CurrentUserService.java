package br.com.condominioauditoria.api.service.user;

import br.com.condominioauditoria.api.dto.response.condominium.CondominiumSummaryResponse;
import br.com.condominioauditoria.api.dto.response.user.CurrentUserResponse;
import br.com.condominioauditoria.api.mapper.CondominiumMapper;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who is logged in, with which roles and in which condominiums. The frontend builds its menu from this. */
@Service
public class CurrentUserService {

    private final CondominiumAccess access;
    private final CondominiumRepository condominiums;

    CurrentUserService(CondominiumAccess access, CondominiumRepository condominiums) {
        this.access = access;
        this.condominiums = condominiums;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me() {
        List<CondominiumSummaryResponse> visible = condominiums.findAll().stream()
                .filter(c -> access.canAccess(c.getId()))
                .map(CondominiumMapper::toSummary)
                .toList();
        return new CurrentUserResponse(access.username(), access.fullName(), access.roles(), visible);
    }
}
