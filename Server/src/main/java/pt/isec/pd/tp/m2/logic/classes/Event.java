package pt.isec.pd.tp.m2.logic.classes;

import java.io.Serial;
import java.io.Serializable;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.List;

public class Event implements Serializable, Remote {
    @Serial
    private static final long serialVersionUID = 1L;
    int id;
    String name;
    String type;
    String location;
    String date;
    String startHour;
    String endHour;
    List<Integer> participants = new ArrayList<>();

    public Event(int id, String name, String type, String location, String date, String startHour, String endHour) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.location = location;
        this.date = date;
        this.startHour = startHour;
        this.endHour = endHour;
    }

    public Event(int id, String name, String type, String location, String date, String startHour, String endHour, List<Integer> participants) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.location = location;
        this.date = date;
        this.startHour = startHour;
        this.endHour = endHour;
        this.participants = participants;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public String getDate() {
        return date;
    }

    public String getStartHour() {
        return startHour;
    }

    public String getEndHour() {
        return endHour;
    }

    public List<Integer> getParticipants() {
        return participants;
    }

    public String toString() {
        return "Event: " + name + " Type: " + type + " Location: " + location + " Date: " + date + " Start Hour: " + startHour + " End Hour: " + endHour;
    }
}
