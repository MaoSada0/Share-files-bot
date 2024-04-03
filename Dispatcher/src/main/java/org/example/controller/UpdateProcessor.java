package org.example.controller;

import lombok.extern.log4j.Log4j;
import org.example.configuration.RabbitConfiguration;
import org.example.service.UpdateProducer;
import org.example.utils.MessageUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;


@Component
@Log4j
public class UpdateProcessor {
    private TelegramBot telegramBot;
    private final MessageUtils messageUtils;
    private final UpdateProducer updateProducer;
    private final RabbitConfiguration rabbitConfiguration;

    public UpdateProcessor(MessageUtils messageUtils, UpdateProducer updateProducer, RabbitConfiguration rabbitConfiguration){
        this.messageUtils = messageUtils;
        this.updateProducer = updateProducer;
        this.rabbitConfiguration = rabbitConfiguration;
    }

    public void registerBot(TelegramBot telegramBot){
        this.telegramBot = telegramBot;
    }

    public void processUpdate(Update update){
        if(update == null){
            log.error("Received update is null");
            return;
        }

        if(update.hasMessage()){
            distributeMessageByType(update);
        } else {
            log.error("Receive unsupported message type: " + update);
        }
    }

    private void distributeMessageByType(Update update) {
        var message = update.getMessage();
        if(message.hasText()){
            processTextMessage(update);
        } else if(message.hasDocument()){
            processDocumentMessage(update);
        } else if(message.hasPhoto()){
            processPhotoMessage(update);
        } else{
            setUnsupportedMessageTypeView(update);
        }
    }

    private void setUnsupportedMessageTypeView(Update update) {
        var sendMessage = messageUtils.generateAnswerMessageWithText(update,
                "Неподдерживаемый тип сообщения!");
        setView(sendMessage);
    }

    private void setFileReceivedView(Update update) {
        var sendMessage = messageUtils.generateAnswerMessageWithText(update,
                "Файл получен, обрабатывается...");
        setView(sendMessage);
    }


    public void setView(SendMessage sendMessage) {
        telegramBot.sendAnswerMessage(sendMessage);
    }

    private void processPhotoMessage(Update update) {
        updateProducer.produce(rabbitConfiguration.getPhotoMessageUpdateQueue(), update);
        setFileReceivedView(update);
    }

    private void processDocumentMessage(Update update) {
        updateProducer.produce(rabbitConfiguration.getDocMessageUpdateQueue(), update);
        setFileReceivedView(update);
    }

    private void processTextMessage(Update update) {
        updateProducer.produce(rabbitConfiguration.getTextMessageUpdateQueue(), update);
    }
}
