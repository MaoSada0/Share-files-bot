package org.example.service.impl;

import lombok.extern.log4j.Log4j;
import org.example.dao.AppUserDAO;
import org.example.dao.RawDataDAO;
import org.example.entity.AppDocument;
import org.example.entity.AppPhoto;
import org.example.entity.AppUser;
import org.example.entity.RawData;
import org.example.exceptions.UploadFileException;
import org.example.service.AppUserService;
import org.example.service.FileService;
import org.example.service.MainService;
import org.example.service.ProducerService;
import org.example.service.enums.LinkType;
import org.example.service.enums.ServiceCommands;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import javax.transaction.Transactional;

import static org.example.entity.enums.UserState.BASIC_STATE;
import static org.example.entity.enums.UserState.WAIT_FOR_EMAIL_STATE;
import static org.example.service.enums.ServiceCommands.*;

@Service
@Log4j
public class MainServiceImpl implements MainService {
    private final RawDataDAO rawDataDAO;
    private final ProducerService producerService;
    private final AppUserDAO appUserDAO;
    private final FileService fileService;
    private final AppUserService appUserService;

    public MainServiceImpl(RawDataDAO rawDataDAO, ProducerService producerService, AppUserDAO appUserDAO, FileService fileService, AppUserService appUserService) {
        this.rawDataDAO = rawDataDAO;
        this.producerService = producerService;
        this.appUserDAO = appUserDAO;
        this.fileService = fileService;
        this.appUserService = appUserService;
    }

    @Transactional
    @Override
    public void processTextMessage(Update update) {
        saveRawData(update);
        var appUser = findOrSaveAppUser(update);
        var userState = appUser.getState();
        var text = update.getMessage().getText();
        var output = "";

        var serviceCommand = ServiceCommands.fromValue(text);
        if(CANCEL.equals(serviceCommand)){
            output = cancelProcess(appUser);
        } else if (BASIC_STATE.equals(userState)) {
            output = processServiceCommand(appUser, text);
        } else if (WAIT_FOR_EMAIL_STATE.equals(userState)) {
            output = appUserService.setEmail(appUser, text);
        } else {
            log.error("Unknown user state: " + userState);
            output = "Unknown error; /cancel";
        }

        var chatId = update.getMessage().getChatId();
        sendAnswer(output, chatId);
    }

    @Override
    public void processDocMessage(Update update) {
        saveRawData(update);
        var appUser = findOrSaveAppUser(update);
        var chatId = update.getMessage().getChatId();

        if(isNotAllowToSendContent(chatId, appUser)){
            return;
        }

        try {
            AppDocument appDoc = fileService.processDoc(update.getMessage());
            String link = fileService.generateLink(appDoc.getId(), LinkType.GET_DOC);
            var answer = "Документ успешно загружен, вот ссылка: " + link;
            sendAnswer(answer, chatId);
        } catch (UploadFileException e){
            log.error(e);
            String error = "Загрузка файла не удалась :(";
            sendAnswer(error, chatId);
        }
    }



    @Override
    public void processPhotoMessage(Update update) {
        saveRawData(update);
        var appUser = findOrSaveAppUser(update);
        var chatId = update.getMessage().getChatId();

        if(isNotAllowToSendContent(chatId, appUser)){
            return;
        }

        try{
            AppPhoto appPhoto = fileService.processPhoto(update.getMessage());
            String link = fileService.generateLink(appPhoto.getId(), LinkType.GET_PHOTO);
            var answer = "Фото загружено! Вот ссылка: " + link;
            sendAnswer(answer, chatId);
        } catch (UploadFileException e){
            log.error(e);
            String error = "Загрузка фото не удалась :(";
            sendAnswer(error, chatId);
        }


    }


    private boolean isNotAllowToSendContent(Long chatId, AppUser appUser) {
        var userState = appUser.getState();
        if(!appUser.isActive()){
            var error = "Зарегестрируйтесь!";
            sendAnswer(error, chatId);
            return true;
        } else if(!BASIC_STATE.equals(userState)){
            var error = "В данный момент вы используете какую то комнаду";
            sendAnswer(error, chatId);
            return true;
        }
        return false;
    }
    private void sendAnswer(String output, Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(output);
        producerService.producerAnswer(sendMessage);

    }

    private String processServiceCommand(AppUser appUser, String cmd) {
        var serviceCommand = ServiceCommands.fromValue(cmd);

        if(REGISTRATION.equals(serviceCommand)){
            return appUserService.registerUser(appUser);
        } else if(HELP.equals(serviceCommand)){
            return help();
        } else if (START.equals(serviceCommand)) {
            return "Hello! /help";
        } else {
            return "ne pon";
        }
    }

    private String help() {
        return "/help\n" +
                "/registration\n" +
                "/cancel\n" +
                "/start";
    }

    private String cancelProcess(AppUser appUser) {
        appUser.setState(BASIC_STATE);
        appUserDAO.save(appUser);

        return "Команда отменена!";
    }

    private AppUser findOrSaveAppUser(Update update){
        User telegramUser = update.getMessage().getFrom();

        var optionalAppUser = appUserDAO.findByTelegramUserId(telegramUser.getId());
        if(optionalAppUser.isEmpty()){
            AppUser transientAppUser = AppUser.builder()
                    .telegramUserId(telegramUser.getId())
                    .username(telegramUser.getUserName())
                    .firstName(telegramUser.getFirstName())
                    .lastName(telegramUser.getLastName())
                    .isActive(false)
                    .state(BASIC_STATE)
                    .build();
            return appUserDAO.save(transientAppUser);
        }
        return optionalAppUser.get();
    }

    private void saveRawData(Update update) {
        RawData rawData = RawData.builder()
                            .event(update)
                            .build();
        rawDataDAO.save(rawData);
    }


}
