package pt.isec.pd.tp.m2.logic;

import java.rmi.Remote;

public interface RemoteBackupServidorInterface extends Remote {
    byte[] getDatabase() throws Exception;
    void registerRMI() throws Exception;
}
