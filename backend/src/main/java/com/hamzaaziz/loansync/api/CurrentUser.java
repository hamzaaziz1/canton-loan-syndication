package com.hamzaaziz.loansync.api;

import com.hamzaaziz.loansync.ledger.Parties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** The signed-in person, and the one party they act as. */
@Component
public class CurrentUser {

    private final Parties parties;

    public CurrentUser(Parties parties) {
        this.parties = parties;
    }

    public String name() {
        return authentication().getName();
    }

    /** Display name of the person's party, such as "Borrower". */
    public String partyName() {
        return authentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(SecurityConfig.PARTY_AUTHORITY))
                .map(a -> a.substring(SecurityConfig.PARTY_AUTHORITY.length()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, name() + " is not linked to a party"));
    }

    /** Full party id. The party must be one this instance's ledger user may act as. */
    public String partyId() {
        var name = partyName();
        return parties.ids().stream()
                .filter(id -> id.startsWith(name + "::"))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, name + " is not a party of this instance"));
    }

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
