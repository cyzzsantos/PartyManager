package pt.isec.pd.tp.m2.logic;

import pt.isec.pd.tp.m2.logic.classes.Account;
import pt.isec.pd.tp.m2.logic.classes.Event;

import java.rmi.Remote;
import java.util.List;

public interface RemoteServidorInterface extends Remote {
    boolean validateRegister(Account account) throws Exception;
    int validateLogin(Account account) throws Exception;
    boolean passwordChangeRequest(Account account, String password) throws Exception;
    boolean emailChangeRequest(Account account, String email) throws Exception;
    boolean joinEventRequest(Account account, int code) throws Exception;
    List<Event> getJoinedEventsByType(Account account, int type, String data) throws Exception;
}
