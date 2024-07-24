package pt.isec.pd.tp.m2.logic;

import pt.isec.pd.tp.m2.logic.classes.Event;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DbManager {
    public static ReadWriteLock lock = new ReentrantReadWriteLock();
    public static Lock readLock = lock.readLock();
    public static Lock writeLock = lock.writeLock();
    private String dbPath = "";
    private static DbManager singleton = null;
    private Connection connection;

    //  Database Setup

    private void connect() {
        try {
            this.connection = DriverManager.getConnection(this.dbPath);
            System.out.println("Conexão com o SQLite estabelecida");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            this.closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (this.connection != null) {
                this.connection.close();
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private String createUsersTable() {
        return "CREATE TABLE IF NOT EXISTS accounts (accountNumber integer PRIMARY KEY, email text NOT NULL, password text NOT NULL, type text NOT NULL)";
    }

    private String createAdministratorsTable() {
        return "CREATE TABLE IF NOT EXISTS administrators (username text PRIMARY KEY, password text NOT NULL)";
    }

    private String createEventsTable() {
        return "CREATE TABLE IF NOT EXISTS events (id integer PRIMARY KEY, name text NOT NULL, type text NOT NULL, location text NOT NULL, date text NOT NULL, startHour text NOT NULL, endHour text NOT NULL, participants text NOT NULL )";
    }

    private String createCodesTable() {
        return "CREATE TABLE IF NOT EXISTS codes(code integer PRIMARY KEY, duration text NOT NULL, eventId integer NOT NULL)";
    }

    private String createMetadataTable() {
        return "CREATE TABLE IF NOT EXISTS metadata(version interger PRIMARY KEY NOT NULL)";
    }

    private void createTable() {
        String accountsTableCreateQuery =  createUsersTable();
        String administratorsTableCreateQuery = createAdministratorsTable();
        String eventsTableCreateQuery = createEventsTable();
        String codesTableCreateQuery = createCodesTable();
        String metadataTableCreateQuery = createMetadataTable();

        try {
            Statement stmt = this.connection.createStatement();
            stmt.execute(accountsTableCreateQuery);
            stmt.execute(administratorsTableCreateQuery);
            stmt.execute(eventsTableCreateQuery);
            stmt.execute(codesTableCreateQuery);
            stmt.execute(metadataTableCreateQuery);

            String query = "SELECT COUNT(*) FROM metadata";
            ResultSet rs = stmt.executeQuery(query);
            if (rs.getInt(1) == 0) {
                String insertQuery = "INSERT INTO metadata(version) VALUES(0)";
                PreparedStatement versionStmt = this.connection.prepareStatement(insertQuery);
                versionStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static synchronized DbManager getInstance() {
        return singleton;
    }

    public DbManager(String dbAddress, String dbName) throws ClassNotFoundException {
        singleton = new DbManager(dbAddress, dbName,0);
    }

    private DbManager(String dbAddress, String dbName, int filler) throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        this.dbPath = "jdbc:sqlite:" + dbAddress + "/" + dbName;
        this.connect();
        this.createTable();
        singleton = this;
    }

    //  User Instructions

    public boolean registerAccount(int accountNumber, String email, String password, String type) {
        String insertQuery = "INSERT INTO accounts(accountNumber, email, password, type) VALUES(?,?,?,?)";

        try {
            PreparedStatement stmt = this.connection.prepareStatement(insertQuery);
            stmt.setInt(1, accountNumber);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.setString(4, type);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public String login(String email, String password) {
        if(!findEmail(email)) {
            return "error";
        }

        readLock.lock();
        String query = "SELECT type FROM accounts WHERE email = ? AND password = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.getString(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "error";
        } finally {
            readLock.unlock();
        }

    }

    public boolean changeAccountEmail(int accountNumber, String newEmail) {
        String query = "UPDATE accounts SET email = ? WHERE accountNumber = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, newEmail);
            stmt.setInt(2, accountNumber);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean changeAccountPassword(int accountNumber, String password) {
        String query = "UPDATE accounts SET password = ? WHERE accountNumber = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, password);
            stmt.setInt(2, accountNumber);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    //  Admin Instructions

    public boolean adminLogin(String username, String password) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM administrators WHERE username = ? AND password = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        } finally {
            readLock.unlock();
        }
    }

    public boolean createEvent(int id, String name, String type, String location, String date, String startHour, String endHour) {
        String insertQuery = "INSERT INTO events(id, name, type, location, date, startHour, endHour, participants) VALUES(?,?,?,?,?,?,?,?)";

        try {
            PreparedStatement stmt = this.connection.prepareStatement(insertQuery);
            stmt.setInt(1, id);
            stmt.setString(2, name);
            stmt.setString(3, type);
            stmt.setString(4, location);
            stmt.setString(5, date);
            stmt.setString(6, startHour);
            stmt.setString(7, endHour);
            stmt.setString(8, "");
            incrementVersion();
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Nao foi possivel registar o evento na base de dados.\n" + e.getMessage());
            return false;
        }
    }

    public boolean changeEventDate(int id, String date) {
        String query1 = "SELECT participants FROM events WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query1);
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            String participants = rs.getString(1);
            if (participants.isEmpty()) {
                String query2 = "UPDATE events SET date = ? WHERE id = ?";
                stmt = this.connection.prepareStatement(query2);
                stmt.setString(1, date);
                stmt.setInt(2, id);
                stmt.executeUpdate();
                incrementVersion();
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean changeEventName(int id, String name) {
        String query = "UPDATE events SET name = ? WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, name);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean changeEventType(int id, String type) {
        String query = "UPDATE events SET type = ? WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, type);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean changeEventLocation(int id, String location) {
        String query = "UPDATE events SET location = ? WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, location);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean changeEventStartHour(int id, String startHour) {
        String query = "UPDATE events SET startHour = ? WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, startHour);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean changeEventEndHour(int id, String endHour) {
        String query = "UPDATE events SET endHour = ? WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, endHour);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean deleteEventById(int id) {
        String query1 = "SELECT participants FROM events WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query1);
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            String participants = rs.getString(1);
            if (participants.isEmpty()) {
                String query2 = "DELETE FROM events WHERE id = ?";
                stmt = this.connection.prepareStatement(query2);
                stmt.setInt(1, id);
                stmt.executeUpdate();
                incrementVersion();
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean deleteEventByName(String name) {
        String query1 = "SELECT participants FROM events WHERE name = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query1);
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            String participants = rs.getString(1);
            if (participants.isEmpty()) {
                String query2 = "DELETE FROM events WHERE name = ?";
                stmt = this.connection.prepareStatement(query2);
                stmt.setString(1, name);
                stmt.executeUpdate();
                incrementVersion();
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public void forceDeleteEvent(int id) {
        String query = "DELETE FROM events WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, id);
            stmt.executeUpdate();
            incrementVersion();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<Event> getAllEvents(){
        readLock.lock();
        List<Event> events = new ArrayList<>();
        String query = "SELECT * FROM events";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String participantsString = rs.getString(8);
                List<Integer> participants = new ArrayList<>();
                if (!participantsString.isEmpty()) {
                    String[] participantsArray = participantsString.split(",");
                    for (String participant : participantsArray) {
                            participants.add(Integer.parseInt(participant));
                    }
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), participants));
                } else
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
        return events;
    }

    public List<Event> getEventsByName(String name) {
        readLock.lock();
        String query = "SELECT * FROM events WHERE name = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            List<Event> events = new ArrayList<>();
            while (rs.next()) {
                String participantsString = rs.getString(8);
                List<Integer> participants = new ArrayList<>();
                if (!participantsString.isEmpty()) {
                    String[] participantsArray = participantsString.split(",");
                    for (String participant : participantsArray) {
                        participants.add(Integer.parseInt(participant));
                    }
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), participants));
                } else
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
            return events;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public List<Event> getEventsByType(String type) {
        readLock.lock();
        String query = "SELECT * FROM events WHERE type = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, type);
            ResultSet rs = stmt.executeQuery();
            List<Event> events = new ArrayList<>();
            while (rs.next()) {
                String participantsString = rs.getString(8);
                List<Integer> participants = new ArrayList<>();
                if (!participantsString.isEmpty()) {
                    String[] participantsArray = participantsString.split(",");
                    for (String participant : participantsArray) {
                        participants.add(Integer.parseInt(participant));
                    }
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), participants));
                } else
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
            return events;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public List<Event> getEventsByLocation(String location) {
        readLock.lock();
        String query = "SELECT * FROM events WHERE location = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, location);
            ResultSet rs = stmt.executeQuery();
            List<Event> events = new ArrayList<>();
            while (rs.next()) {
                String participantsString = rs.getString(8);
                List<Integer> participants = new ArrayList<>();
                if (!participantsString.isEmpty()) {
                    String[] participantsArray = participantsString.split(",");
                    for (String participant : participantsArray) {
                        participants.add(Integer.parseInt(participant));
                    }
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), participants));
                } else
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
            return events;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public List<Event> getEventsByDate(String date) {
        readLock.lock();
        String query = "SELECT * FROM events WHERE date = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();
            List<Event> events = new ArrayList<>();
            while (rs.next()) {
                String participantsString = rs.getString(8);
                List<Integer> participants = new ArrayList<>();
                if (!participantsString.isEmpty()) {
                    String[] participantsArray = participantsString.split(",");
                    for (String participant : participantsArray) {
                        participants.add(Integer.parseInt(participant));
                    }
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), participants));
                } else
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
            return events;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public boolean addParticipantToEvent(int accountNumber, int eventId) {
        String query1 = "SELECT participants FROM events WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query1);
            stmt.setInt(1, eventId);
            ResultSet rs = stmt.executeQuery();
            String participants = rs.getString(1);
            if (participants.isEmpty()) {
                String query2 = "UPDATE events SET participants = ? WHERE id = ?";
                stmt = this.connection.prepareStatement(query2);
                stmt.setString(1, String.valueOf(accountNumber));
                stmt.setInt(2, eventId);
                stmt.executeUpdate();
                incrementVersion();
                return true;
            } else {
                String query2 = "UPDATE events SET participants = ? WHERE id = ?";
                stmt = this.connection.prepareStatement(query2);
                stmt.setString(1, participants + "," + accountNumber);
                stmt.setInt(2, eventId);
                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean removeParticipantFromEvent(int accountNumber, int eventId) {
        String query1 = "SELECT participants FROM events WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query1);
            stmt.setInt(1, eventId);
            ResultSet rs = stmt.executeQuery();
            String participants = rs.getString(1);
            if (participants.isEmpty()) {
                return false;
            } else {
                String query2 = "UPDATE events SET participants = ? WHERE id = ?";
                stmt = this.connection.prepareStatement(query2);
                stmt.setString(1, participants.replace(accountNumber + ",", ""));
                stmt.setInt(2, eventId);
                stmt.executeUpdate();
                incrementVersion();
                return true;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean createCode(int code, int duration, int eventId) {
        String insertQuery = "INSERT INTO codes(code, duration, eventId) VALUES(?,?,?)";

        try {
            PreparedStatement stmt = this.connection.prepareStatement(insertQuery);
            stmt.setInt(1, code);
            stmt.setInt(2, duration);
            stmt.setInt(3, eventId);
            deleteOlderCode(eventId);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println("Nao foi possivel registar o codigo na base de dados.\n" + e.getMessage());
            return false;
        }
    }

    public boolean createCode(int code, int duration, String name) {
        String insertQuery = "INSERT INTO codes(code, duration, eventId) VALUES(?,?,?)";

        try {
            PreparedStatement stmt = this.connection.prepareStatement(insertQuery);
            stmt.setInt(1, code);
            stmt.setInt(2, duration);
            stmt.setInt(3, getEventIdByName(name));
            deleteOlderCode(getEventIdByName(name));
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println("Nao foi possivel registar o codigo na base de dados.\n" + e.getMessage());
            return false;
        }
    }

    private void deleteOlderCode(int eventId) {
        String query = "DELETE FROM codes WHERE eventId = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, eventId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public boolean deleteCode(int code) {
        String query = "DELETE FROM codes WHERE code = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, code);
            stmt.executeUpdate();
            incrementVersion();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    //  General Tools

    public int getEventIdByName(String name) {
        readLock.lock();
        String query = "SELECT id FROM events WHERE name = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return 0;
        } finally {
            readLock.unlock();
        }
    }

    public Event getEventById(int eventId) {
        readLock.lock();
        String query = "SELECT * FROM events WHERE id = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, eventId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String participantsString = rs.getString(8);
                List<Integer> participants = new ArrayList<>();
                if (!participantsString.isEmpty()) {
                    String[] participantsArray = participantsString.split(",");
                    for (String participant : participantsArray) {
                        participants.add(Integer.parseInt(participant));
                    }
                    return new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), participants);
                }
                else {
                    return new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
                }
            }
            return null;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public Event getEventByName(String name) {
        readLock.lock();
        String query = "SELECT * FROM events WHERE name = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String participantsString = rs.getString(8);
                List<Integer> participants = new ArrayList<>();
                if (!participantsString.isEmpty()) {
                    String[] participantsArray = participantsString.split(",");
                    for (String participant : participantsArray) {
                        participants.add(Integer.parseInt(participant));
                    }
                    return new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), participants);
                }
                else {
                    return new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
                }
            }
            return null;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public int getHighestEventId() {
        readLock.lock();
        String query = "SELECT MAX(id) FROM events";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            return rs.getInt(1) + 1;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return 0;
        } finally {
            readLock.unlock();
        }
    }

    public int getAccountNumberByEmail(String email) {
        readLock.lock();
        String query = "SELECT accountNumber FROM accounts WHERE email = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return 0;
        } finally {
            readLock.unlock();
        }
    }

    public String getEmailByAccountNumber(int accountNumber) {
        readLock.lock();
        String query = "SELECT email FROM accounts WHERE accountNumber = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, accountNumber);
            ResultSet rs = stmt.executeQuery();
            return rs.getString(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public List<Event> getEventsByParticipant(int id) {
        readLock.lock();
        List<Event> events = new ArrayList<>();
        String query = "SELECT * FROM events WHERE participants LIKE ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, "%" + id + "%");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String participantsString = rs.getString(8);
                List<Integer> participants = new ArrayList<>();
                if (!participantsString.isEmpty()) {
                    String[] participantsArray = participantsString.split(",");
                    for (String participant : participantsArray) {
                        participants.add(Integer.parseInt(participant));
                    }
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), participants));
                } else
                    events.add(new Event(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
        return events;
    }

    public Event getEventByCode(int code) {
        readLock.lock();
        String query = "SELECT eventId FROM codes WHERE code = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, code);
            ResultSet rs = stmt.executeQuery();
            return getEventById(rs.getInt(1));
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public List<Integer> getAllCodes() {
        readLock.lock();
        String query = "SELECT code FROM codes";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            List<Integer> codes = new ArrayList<>();
            while (rs.next()) {
                codes.add(rs.getInt(1));
            }
            return codes;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public int getCodeDuration(int code) {
        readLock.lock();
        String query = "SELECT duration FROM codes WHERE code = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, code);
            ResultSet rs = stmt.executeQuery();
            return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return 0;
        } finally {
            readLock.unlock();
        }
    }

    public boolean checkEventById(int id) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM events WHERE id = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            return !rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        } finally {
            readLock.unlock();
        }
    }

    public boolean checkEventByName(String name) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM events WHERE name = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return true;
        } finally {
            readLock.unlock();
        }
    }

    public boolean checkEventByType(String type) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM events WHERE type = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, type);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return true;
        } finally {
            readLock.unlock();
        }
    }

    public boolean checkEventByLocation(String location) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM events WHERE location = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, location);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return true;
        } finally {
            readLock.unlock();
        }
    }

    public boolean checkEventByDate(String date) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM events WHERE date = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return true;
        } finally {
            readLock.unlock();
        }
    }

    public boolean checkUniqueEmail(String email) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM accounts WHERE email = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return true;
        } finally {
            readLock.unlock();
        }
    }

    public boolean checkUniqueAccountNumber(int accountNumber) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM accounts WHERE accountNumber = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, accountNumber);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return true;
        } finally {
            readLock.unlock();
        }
    }

    public boolean checkUniqueEventCode(int code) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM codes WHERE code = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, code);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return true;
        } finally {
            readLock.unlock();
        }
    }

    public void updateCodeDuration(int code, int duration) {
        String query = "UPDATE codes SET duration = ? WHERE code = ?";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setInt(1, duration);
            stmt.setInt(2, code);
            stmt.executeUpdate();
            incrementVersion();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private boolean findEmail(String email) {
        readLock.lock();
        String query = "SELECT EXISTS (SELECT 1 FROM accounts WHERE email = ?)";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        } finally {
            readLock.unlock();
        }
    }

    private void incrementVersion() {
        writeLock.lock();
        String query = "UPDATE metadata SET version = version + 1";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        finally {
            writeLock.unlock();
        }
    }

    public int getVersion() {
        readLock.lock();
        String query = "SELECT version FROM metadata";
        try {
            PreparedStatement stmt = this.connection.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return 0;
        } finally {
            readLock.unlock();
        }
    }
}