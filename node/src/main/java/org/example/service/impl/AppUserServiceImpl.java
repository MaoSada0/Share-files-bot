package org.example.service.impl;

import lombok.extern.log4j.Log4j;
import org.example.dao.AppUserDAO;
import org.example.dto.MailParams;
import org.example.entity.AppUser;
import org.example.service.AppUserService;
import org.example.utils.CryptoTool;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import static org.example.entity.enums.UserState.BASIC_STATE;
import static org.example.entity.enums.UserState.WAIT_FOR_EMAIL_STATE;

@Service
@Log4j
public class AppUserServiceImpl implements AppUserService {
    private final AppUserDAO appUserDAO;
    private final CryptoTool cryptoTool;

    @Value("${spring.rabbitmq.queues.registration-mail}")
    private String registrationMailQueue;

    private final RabbitTemplate rabbitTemplate;

    public AppUserServiceImpl(AppUserDAO appUserDAO, CryptoTool cryptoTool, RabbitTemplate rabbitTemplate) {
        this.appUserDAO = appUserDAO;
        this.cryptoTool = cryptoTool;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public String registerUser(AppUser appUser) {
        if(appUser.isActive()){
            return "Вы уже зарегестрированы!";
        } else if (appUser.getEmail() != null) {
            return "Вам на почту уже было отправлено письмо!";
        }

        appUser.setState(WAIT_FOR_EMAIL_STATE);
        appUserDAO.save(appUser);
        return "Введите ваш email";
    }

    @Override
    public String setEmail(AppUser appUser, String email) {
        try {
            InternetAddress emailAddress = new InternetAddress(email);
            emailAddress.validate();
        } catch (AddressException e) {
            return "Введите, пожалуйста, корректный email! /cancel";
        }

        var appUserOpt = appUserDAO.findByEmail(email);

        if(appUserOpt.isEmpty()){
            appUser.setEmail(email);
            appUser.setState(BASIC_STATE);
            appUser = appUserDAO.save(appUser);

            var cryptoUserId = cryptoTool.hashOf(appUser.getId());
            sendRequestToMailService(cryptoUserId, email);
            return "Вам на почту было отправлено письмо. Перейдите по ссылке в письме для подтверждения регистрации";
        }else {
            return "Этот email уже используется /cancel";
        }
    }

    private void sendRequestToMailService(String cryptoUserId, String email) {

        var mailParams = MailParams.builder()
                .id(cryptoUserId)
                .emailTo(email)
                .build();

        rabbitTemplate.convertAndSend(registrationMailQueue, mailParams);
    }
}
