package pt.isec.pd.tp.m2.logic.classes;

import java.io.Serial;
import java.io.Serializable;
import java.rmi.Remote;

public class Account implements Serializable, Remote {
    @Serial
    private static final long serialVersionUID = 1L;
    int studentNumber;
    String email;
    String password;
    String type = "user";

    public Account(int studentNumber, String email, String password) {
        this.studentNumber = studentNumber;
        this.email = email;
        this.password = password;
    }

    public Account(String email, String password) {
        this.email = email;
        this.password = password;
        this.type = "admin";
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getStudentNumber() {
        return studentNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getType() {
        return type;
    }
}
