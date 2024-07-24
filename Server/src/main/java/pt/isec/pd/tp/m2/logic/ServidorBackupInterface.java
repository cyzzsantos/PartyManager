package pt.isec.pd.tp.m2.logic;

import java.rmi.Remote;

public interface ServidorBackupInterface extends Remote {

    void registerAccount(int studentNumber, String email, String password, String type) throws Exception;

    void changeAccountPassword(int studentNumber, String password) throws Exception;

    void changeAccountEmail(int studentNumber, String newEmail) throws Exception;

    void addParticipantToEvent(int studentNumber, int eventId) throws Exception;

    void createEvent(int id, String name, String type, String location, String date, String startHour, String endHour) throws Exception;

    void changeEventName(int id, String name) throws Exception;

    void changeEventDate(int id, String date) throws Exception;

    void changeEventType(int id, String type) throws Exception;

    void changeEventLocation(int id, String location) throws Exception;

    void changeEventStartHour(int id, String startHour) throws Exception;

    void changeEventEndHour(int id, String endHour) throws Exception;

    void deleteEvent(int id) throws Exception;

    void createCode(int code, int duration, int eventId) throws Exception;

    void removeParticipantFromEvent(int studentNumber, int eventId) throws Exception;

    void updateCodeDuration(int code, int duration) throws Exception;

    void deleteCode(int code) throws Exception;
}
