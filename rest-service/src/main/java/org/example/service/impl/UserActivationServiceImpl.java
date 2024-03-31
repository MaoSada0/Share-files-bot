package org.example.service.impl;

import org.example.dao.AppUserDAO;
import org.example.service.UserActivationService;
import org.example.utils.CryptoTool;
import org.springframework.stereotype.Service;

@Service
public class UserActivationServiceImpl implements UserActivationService {
    private final AppUserDAO appUserDAO;
    private final CryptoTool cryptoTool;

    public UserActivationServiceImpl(AppUserDAO appUserDAO, CryptoTool cryptoTool) {
        this.appUserDAO = appUserDAO;
        this.cryptoTool = cryptoTool;
    }

    @Override
    public boolean activation(String cryptoUserId) {
        var userId = cryptoTool.idOf(cryptoUserId);
        var optional = appUserDAO.findById(userId);
        if(optional.isPresent()){
            var user = optional.get();
            user.setActive(true);
            appUserDAO.save(user);
            return true;
        }

        return false;
    }
}
