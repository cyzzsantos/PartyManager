package pt.isec.pd.tp.m2.logic;

import org.springframework.boot.SpringApplication;
import pt.isec.pd.tp.m2.Application;
import pt.isec.pd.tp.m2.logic.Tools.DateChecker;
import pt.isec.pd.tp.m2.logic.Tools.EmailChecker;
import pt.isec.pd.tp.m2.logic.Tools.HourChecker;
import pt.isec.pd.tp.m2.logic.classes.Account;
import pt.isec.pd.tp.m2.logic.classes.Event;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static java.lang.Integer.parseInt;
import static java.lang.System.exit;

public class RemoteServidor extends UnicastRemoteObject implements RemoteServidorInterface, Runnable {

    public static final Lock writeLock = DbManager.writeLock;
    private static Boolean loggedIn = false;
    private static DbManager dbManager;
    private static Account account;
    private static final List<ServidorBackupInterface> ref = new ArrayList<>();

    private static int clientPort;
    private static String dbAddress;
    private static String dbName;
    private static int registryPort;
    private static String registryServiceName;


    static class RemoteBackupServidor extends UnicastRemoteObject implements RemoteBackupServidorInterface {
        @Override
        public byte[] getDatabase() {
            byte[] fileBytes;
            writeLock.lock();
            try {
                File databaseFile = new File(dbAddress + dbName);
                fileBytes = Files.readAllBytes(databaseFile.toPath());
                return fileBytes;
            }
            catch (IOException e) {
                return null;
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void registerRMI() {
            registerBackupCallback();
        }

        RemoteBackupServidor() throws Exception {
            super();
        }
    }

    //  User Instructions

    @Override
    public boolean validateRegister(Account account) throws Exception {
        System.out.println("A criar conta para " + account.getEmail() + "...");
        if(account.getStudentNumber() > 999999999 || account.getStudentNumber() < 100000000) {
            System.out.println("Numero de estudante inválido.");
            return false;
        }
        if(dbManager.checkUniqueAccountNumber(account.getStudentNumber())) {
            System.out.println("Numero de estudante já registado.");
            return false;
        }
        System.out.println("Numero de estudante válido.");

        if(EmailChecker.validate(account.getEmail())) {
            System.out.println("Email inválido.");
            return false;
        }
        if(dbManager.checkUniqueEmail(account.getEmail())) {
            System.out.println("Email já registado.");
            return false;
        }
        System.out.println("Email válido.");

        if(account.getPassword().length() < 8) {
            System.out.println("Password inválida.");
            return false;
        }
        System.out.println("Password válida.");

        System.out.println("A registar conta na base de dados...");
        writeLock.lock();
        if(dbManager.registerAccount(account.getStudentNumber(), account.getEmail(), account.getPassword(), account.getType())) {
            System.out.println("Conta registada com sucesso.");
            for(ServidorBackupInterface ref: ref) {
                ref.registerAccount(account.getStudentNumber(), account.getEmail(), account.getPassword(), account.getType());
            }
            sendHeartbeat();
            writeLock.unlock();
            return true;
        }
        System.out.println("Erro ao tentar registar conta.");
        writeLock.unlock();

        return false;
    }

    @Override
    public int validateLogin(Account account) {
        /*
        if(dbManager.login(account.getEmail(), account.getPassword())) {
            return dbManager.getAccountNumberByEmail(account.getEmail());
        }*/

        return 0;
    }

    @Override
    public boolean passwordChangeRequest(Account account, String password) throws Exception {
        writeLock.lock();
        if(dbManager.changeAccountPassword(account.getStudentNumber(), password)) {
            for (ServidorBackupInterface ref : ref)
                ref.changeAccountPassword(account.getStudentNumber(), password);
            sendHeartbeat();
            writeLock.unlock();
            return true;
        }
        writeLock.unlock();
        return false;
    }

    @Override
    public boolean emailChangeRequest(Account account, String email) throws Exception {
        if(EmailChecker.validate(email)) {
            return false;
        }

        writeLock.lock();
        if(dbManager.changeAccountEmail(account.getStudentNumber(), email)) {
            for(ServidorBackupInterface ref: ref)
                ref.changeAccountEmail(account.getStudentNumber(), email);
            sendHeartbeat();
            writeLock.unlock();
            return true;
        }
        writeLock.unlock();
        return false;
    }

    @Override
    public boolean joinEventRequest(Account account, int code) throws Exception {
        Event event = dbManager.getEventByCode(code);
        if(event == null)
            return false;

        List<Event> eventsAtSameDate = dbManager.getEventsByDate(event.getDate());
        eventsAtSameDate.removeIf(RemoteServidor::eventoADecorrer);

        for(Event e : eventsAtSameDate) {
            for(int participantId : e.getParticipants()) {
                if(participantId == account.getStudentNumber())
                    return false;
            }
        }

        writeLock.lock();
        if(dbManager.addParticipantToEvent(account.getStudentNumber(), event.getId())) {
            for(ServidorBackupInterface ref: ref)
                ref.addParticipantToEvent(account.getStudentNumber(), event.getId());
            sendHeartbeat();
            writeLock.unlock();
            return true;
        }
        writeLock.unlock();
        return false;
    }

    @Override
    public List<Event> getJoinedEventsByType(Account account, int type, String data) {
        List<Event> eventsList = new ArrayList<>();
        switch(type) {
            case 1:
                eventsList = dbManager.getEventsByName(data);
                eventsList.removeIf(e -> !e.getParticipants().contains(account.getStudentNumber()));
                break;
            case 2:
                eventsList = dbManager.getEventsByType(data);
                eventsList.removeIf(e -> !e.getParticipants().contains(account.getStudentNumber()));
                break;
            case 3:
                eventsList = dbManager.getEventsByLocation(data);
                eventsList.removeIf(e -> !e.getParticipants().contains(account.getStudentNumber()));
                break;
            case 4:
                eventsList = dbManager.getEventsByDate(data);
                eventsList.removeIf(e -> !e.getParticipants().contains(account.getStudentNumber()));
                break;
            case 5:
                eventsList = dbManager.getAllEvents();
                eventsList.removeIf(e -> !e.getParticipants().contains(account.getStudentNumber()));

                break;
            default:
                break;
        }
        return eventsList;
    }

    //  Admin Instructions

    private static void login() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Insira o seu username:");
        String username = scanner.nextLine();
        System.out.println("Insira a sua password:");
        String password = scanner.nextLine();

        if(dbManager.adminLogin(username, password)) {
            System.out.println("Login efetuado com sucesso.");
            RemoteServidor.account = new Account(username, password);
            loggedIn = true;
        }
        else
            System.out.println("Username ou password invalida");
    }

    private static void createEvent() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Insira o nome do evento:");
        String name = scanner.nextLine();
        if(dbManager.checkEventByName(name)) {
            System.out.println("Já existe um evento com este nome.");
            return;
        }
        System.out.println("Insira o tipo do evento:");
        String type = scanner.nextLine();
        System.out.println("Insira o local do evento:");
        String location = scanner.nextLine();
        System.out.println("Insira a data do evento \nFormato: \"dia-mes-ano\"");
        String date = scanner.nextLine();
        if(DateChecker.validate(date)) {
            System.out.println("Data inválida.");
            return;
        }

        System.out.println("Insira a hora de inicio do evento \nFormato: \"hora:minuto\"");
        String startHour = scanner.nextLine();
        if (HourChecker.validate(startHour)) {
            System.out.println("Hora inválida.");
            return;
        }

        System.out.println("Insira a hora de fim do evento \nFormato: \"hora:minuto\"");
        String endHour = scanner.nextLine();
        if (HourChecker.validate(endHour)) {
            System.out.println("Hora inválida.");
            return;
        }

        if (Integer.parseInt(startHour.split(":")[0]) > Integer.parseInt(endHour.split(":")[0])) {
            System.out.println("Hora inválida - Hora de fim inferior à hora de inicio do evento.");
            return;
        }
        else if (Integer.parseInt(startHour.split(":")[0]) == Integer.parseInt(endHour.split(":")[0])) {
            if (Integer.parseInt(startHour.split(":")[1]) > Integer.parseInt(endHour.split(":")[1])) {
                System.out.println("Hora inválida - Hora de fim inferior à hora de inicio do evento.");
                return;
            }
        }

        // Considerando as ultimas duas condições, não é possível registar eventos
        // que se extendam entre dois dias consecutivos.

        Thread thread = new Thread(() -> {
            writeLock.lock();
            if(dbManager.createEvent(dbManager.getHighestEventId(), name, type, location, date, startHour, endHour)) {
                System.out.println("Evento criado com sucesso.");
                writeLock.unlock();
                for (ServidorBackupInterface ref : ref) {
                    try {
                        ref.createEvent(dbManager.getHighestEventId(), name, type, location, date, startHour, endHour);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                sendHeartbeat();
            }
            else {
                System.out.println("Erro ao criar o evento.");
                writeLock.unlock();
            }
        });

        System.out.println("A criar evento...");
        thread.start();
    }

    private static void editEvent() {
        Thread thread;
        int option;
        Scanner scanner = new Scanner(System.in);

        System.out.println("Insira o nome do evento:");
        int id = dbManager.getEventIdByName(scanner.nextLine());

        if(id == 0) {
            System.out.println("Evento inválido.");
            return;
        }

        System.out.println("O que deseja alterar neste evento?");
        System.out.println("1 - Nome");
        System.out.println("2 - Data");
        System.out.println("3 - Tipo");
        System.out.println("4 - Local");
        System.out.println("5 - Hora de Inicio");
        System.out.println("6 - Hora de Fim");
        try {
            option = parseInt(scanner.nextLine());
        }
        catch (NumberFormatException e) {
            System.out.println("Opção inválida.");
            return;
        }

        switch(option) {
            case 1:
                System.out.println("Insira o novo nome do evento");
                String name = scanner.nextLine();
                if(dbManager.checkEventByName(name)) {
                    System.out.println("Já existe um evento com este nome.");
                    return;
                }

                thread = new Thread(() -> {
                    writeLock.lock();
                    if(dbManager.changeEventName(id, name)) {
                        writeLock.unlock();
                        for (ServidorBackupInterface ref : ref) {
                            try {
                                ref.changeEventName(id, name);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        System.out.println("Nome alterado com sucesso.");
                        sendHeartbeat();
                    }
                    else {
                        System.out.println("Erro ao alterar o nome.");
                        writeLock.unlock();
                    }
                });

                System.out.println("A alterar o nome do evento...");
                thread.start();
                break;

            case 2:
                System.out.println("Insira a nova data do evento");
                String date = scanner.nextLine();
                if(DateChecker.validate(date)) {
                    System.out.println("Data inválida.");
                    return;
                }

                thread = new Thread(() -> {
                    writeLock.lock();
                    if(dbManager.changeEventDate(id, date)) {
                        writeLock.unlock();
                        for (ServidorBackupInterface ref : ref) {
                            try {
                                ref.changeEventDate(id, date);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        System.out.println("Data alterada com sucesso.");
                        sendHeartbeat();
                    }
                    else {
                        System.out.println("Erro ao alterar a data.");
                        writeLock.unlock();
                    }
                });

                System.out.println("A alterar a data do evento...");
                thread.start();
                break;

            case 3:
                System.out.println("Insira o novo tipo do evento:");
                String type = scanner.nextLine();

                thread = new Thread(() -> {
                    writeLock.lock();
                    if(dbManager.changeEventType(id, type)) {
                        writeLock.unlock();
                        for(ServidorBackupInterface ref: ref) {
                            try {
                                ref.changeEventType(id, type);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        System.out.println("Tipo alterado com sucesso.");
                        sendHeartbeat();
                    }
                    else {
                        System.out.println("Erro ao alterar o tipo.");
                        writeLock.unlock();
                    }
                });

                System.out.println("A alterar tipo do evento...");
                thread.start();
                break;

            case 4:
                System.out.println("Insira o novo local do evento:");
                String location = scanner.nextLine();

                thread = new Thread(() -> {
                    writeLock.lock();
                    if(dbManager.changeEventLocation(id, location)) {
                        writeLock.unlock();
                        for(ServidorBackupInterface ref: ref) {
                            try {
                                ref.changeEventLocation(id, location);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        System.out.println("Local alterado com sucesso.");
                        sendHeartbeat();
                    }
                    else {
                        System.out.println("Erro ao alterar o local.");
                        writeLock.unlock();
                    }
                });

                System.out.println("A alterar local do evento...");
                thread.start();
                break;

            case 5:
                System.out.println("Insira a nova hora de inicio do evento:");
                String startHour = scanner.nextLine();
                if (HourChecker.validate(startHour)) {
                    System.out.println("Hora inválida.");
                    return;
                }

                if (Integer.parseInt(startHour.split(":")[0]) > Integer.parseInt(dbManager.getEventById(id).getEndHour().split(":")[0])) {
                    System.out.println("Hora inválida - Hora de inicio superior à hora de fim do evento.");
                    return;
                }
                else if (Integer.parseInt(startHour.split(":")[0]) == Integer.parseInt(dbManager.getEventById(id).getEndHour().split(":")[0])) {
                    if (Integer.parseInt(startHour.split(":")[1]) > Integer.parseInt(dbManager.getEventById(id).getEndHour().split(":")[1])) {
                        System.out.println("Hora inválida - Hora de inicio superior à hora de fim do evento.");
                        return;
                    }
                }

                thread = new Thread(() -> {
                    writeLock.lock();
                    if(dbManager.changeEventStartHour(id, startHour)) {
                        writeLock.unlock();
                        for(ServidorBackupInterface ref: ref) {
                            try {
                                ref.changeEventStartHour(id, startHour);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        System.out.println("Hora de inicio alterada com sucesso.");
                        sendHeartbeat();
                    }
                    else {
                        System.out.println("Erro ao alterar a hora de inicio.");
                        writeLock.unlock();
                    }
                });

                System.out.println("A alterar hora de inicio...");
                thread.start();
                break;

            case 6:
                System.out.println("Insira a nova hora de fim do evento:");
                String endHour = scanner.nextLine();
                if (HourChecker.validate(endHour)) {
                    System.out.println("Hora inválida.");
                    return;
                }

                if (Integer.parseInt(endHour.split(":")[0]) < Integer.parseInt(dbManager.getEventById(id).getStartHour().split(":")[0])) {
                    System.out.println("Hora inválida - Hora de fim inferior à hora de inicio do evento.");
                    return;
                }
                else if (Integer.parseInt(endHour.split(":")[0]) == Integer.parseInt(dbManager.getEventById(id).getStartHour().split(":")[0])) {
                    if (Integer.parseInt(endHour.split(":")[1]) < Integer.parseInt(dbManager.getEventById(id).getStartHour().split(":")[1])) {
                        System.out.println("Hora inválida - Hora de fim inferior à hora de inicio do evento.");
                        return;
                    }
                }

                thread = new Thread(() -> {
                    writeLock.lock();
                    if(dbManager.changeEventEndHour(id, endHour)) {
                        writeLock.unlock();
                        for(ServidorBackupInterface ref: ref) {
                            try {
                                ref.changeEventEndHour(id, endHour);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        System.out.println("Hora de fim alterada com sucesso.");
                        sendHeartbeat();
                    }
                    else {
                        System.out.println("Erro ao alterar a hora de fim.");
                        writeLock.unlock();
                    }
                });

                System.out.println("A alterar hora de fim...");
                thread.start();
                break;

            default:
                System.out.println("Opção inválida.");
        }
    }

    private static void deleteEvent() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o nome do evento:");
        String name = scanner.nextLine();

        if(dbManager.getEventIdByName(name) == 0) {
            System.out.println("Evento inválido.");
            return;
        }

        Thread thread = new Thread(() -> {
            writeLock.lock();
            if(dbManager.deleteEventById(dbManager.getEventIdByName(name))) {
                writeLock.unlock();
                for(ServidorBackupInterface ref: ref) {
                    try {
                        ref.deleteEvent(dbManager.getEventIdByName(name));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                sendHeartbeat();
                System.out.println("Evento " + name + " eliminado.");
            }
            else {
                System.out.println("Erro ao eliminar o evento " + name + ".");
                writeLock.unlock();
            }
        });

        System.out.println("A eliminar evento...");
        thread.start();
    }

    private static void checkEvents() {
        Scanner scanner = new Scanner(System.in);
        List<Event> eventos;
        System.out.println("Escolha o critério a utilizar:");
        System.out.println("1 - Nome");
        System.out.println("2 - Tipo");
        System.out.println("3 - Local");
        System.out.println("4 - Data");
        System.out.println("5 - Nenhum");
        int option = new Scanner(System.in).nextInt();

        switch(option) {
            case 1:
                System.out.println("Insira o nome:");
                String name = scanner.nextLine();
                eventos = dbManager.getEventsByName(name);
                printEventsList(eventos);
                break;

            case 2:
                System.out.println("Insira o tipo:");
                String type = scanner.nextLine();
                eventos = dbManager.getEventsByType(type);
                printEventsList(eventos);
                break;

            case 3:
                System.out.println("Insira o local:");
                String location = scanner.nextLine();
                eventos = dbManager.getEventsByLocation(location);
                printEventsList(eventos);
                break;

            case 4:
                System.out.println("Insira a data (dia-mes-ano):");
                String date = scanner.nextLine();
                if(DateChecker.validate(date)) {
                    System.out.println("Data inválida.");
                    return;
                }
                eventos = dbManager.getEventsByDate(date);
                printEventsList(eventos);
                break;

            case 5:
                eventos = dbManager.getAllEvents();
                printEventsList(eventos);
                break;

            default:
                System.out.println("Opção inválida.");
        }
    }

    private static void generateCode()  {
        Scanner scanner = new Scanner(System.in);
        int eventId;

        System.out.println("Insira o id do evento que pretende gerar um código:");
        try {
            eventId = parseInt(scanner.nextLine());
        }
        catch (NumberFormatException e) {
            System.out.println("Evento inválido.");
            return;
        }

        if(dbManager.checkEventById(eventId)) {
            System.out.println("Evento inválido.");
            return;
        }

        if(eventoADecorrer(dbManager.getEventById(eventId))) {
            System.out.println("Só é possivel criar códigos para eventos que estejam a decorrer.");
            return;
        }

        System.out.println("Durante quantos minutos deve ser este código válido?");
        int duration = parseInt(scanner.nextLine());

        int code = CodeGenerator();
        while(dbManager.checkUniqueEventCode(code)) {
            code = CodeGenerator();
        }

        writeLock.lock();
        if(dbManager.createCode(code, duration, eventId)) {
            writeLock.unlock();
            for (ServidorBackupInterface ref : ref) {
                try {
                    ref.createCode(code, duration, eventId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            sendHeartbeat();
            System.out.println("Código " + code + " gerado com sucesso.");
        }
        else {
            System.out.println("Erro ao gerar o código.");
            writeLock.unlock();
        }
    }

    private static void checkAttendence() {
        int eventId;
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o id do evento:");

        try {
            eventId = parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Evento inválido.");
            return;
        }

        if(dbManager.checkEventById(eventId)) {
            System.out.println("Evento inválido.");
            return;
        }
        Event event = dbManager.getEventById(eventId);

        List<Integer> ids = new ArrayList<>();

        System.out.println("Lista de participantes do evento " + event.getName() + ":");
        if(event.getParticipants().isEmpty())
            System.out.println("(lista vazia)");
        for(int cc : event.getParticipants()) {
            ids.add(cc);
            System.out.println(cc + " " + dbManager.getEmailByAccountNumber(cc));
        }

        System.out.println("Deseja converter esta lista para um ficheiro CSV? (s/n)");
        String option = scanner.nextLine();
        if(option.equals("s") || option.equals("S")) {
            convertEventsToCsv(event, ids);
        }
    }

    private static void checkUserAttendence() {
        int userId;
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o id do utilizador:");

        try {
            userId = parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Evento inválido.");
            return;
        }

        if(!dbManager.checkUniqueAccountNumber(userId)) {
            System.out.println("Utilizador inválido.");
            return;
        }

        List<Event> events = dbManager.getEventsByParticipant(userId);
        if(events.isEmpty()) {
            System.out.println("O utilizador não participa em nenhum evento.");
            return;
        }

        System.out.println("Lista de eventos em que o utilizador participou:");
        for(Event event : events) {
            System.out.println(event.getName());
        }

        System.out.println("Deseja converter esta lista para um ficheiro CSV? (s/n)");
        String option = scanner.nextLine();
        if(option.equals("s") || option.equals("S")) {
            convertUserEventsToCsv(events, userId);
        }

    }

    private static void deleteAttendence() {
        Scanner scanner = new Scanner(System.in);
        int eventId;

        System.out.println("Insira o id do evento: ");
        try {
            eventId = parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Evento inválido.");
            return;
        }
        System.out.println("Insira o email do utilizador: ");
        String email = scanner.nextLine();

        int id = dbManager.getAccountNumberByEmail(email);
        if(id == 0)
            System.out.println("Utilizador inválido.");
        if(dbManager.checkEventById(eventId)) {
            System.out.println("Evento inválido.");
            return;
        }


        Thread thread = new Thread(() -> {
            writeLock.lock();
            if(dbManager.removeParticipantFromEvent(id, eventId)) {
                writeLock.unlock();
                for (ServidorBackupInterface ref : ref) {
                    try {
                        ref.removeParticipantFromEvent(id, eventId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                sendHeartbeat();
                System.out.println("Participação removida com sucesso.");
            }
            else {
                System.out.println("Erro ao remover a participação.");
                writeLock.unlock();
            }
        });

        System.out.println("A remover participação...");
        thread.start();
    }

    private static void insertAttendence() {
        int eventId;
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o id do evento: ");
        try {
            eventId = parseInt(scanner.nextLine());
        }
        catch (NumberFormatException e) {
            System.out.println("Evento inválido.");
            return;
        }

        System.out.println("Insira o email do utilizador: ");
        String email = scanner.nextLine();

        int id = dbManager.getAccountNumberByEmail(email);
        if(id == 0)
            System.out.println("Utilizador inválido.");
        if(dbManager.checkEventById(eventId))
            System.out.println("Evento inválido.");

        Thread thread = new Thread(() -> {
            writeLock.lock();
            if(dbManager.addParticipantToEvent(id, eventId)) {
                writeLock.unlock();
                for (ServidorBackupInterface ref : ref) {

                    try {
                        ref.addParticipantToEvent(id, eventId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                sendHeartbeat();
                System.out.println("Participação adicionada com sucesso.");
            }
            else {
                System.out.println("Erro ao adicionar a participação.");
                writeLock.unlock();
            }
        });

        System.out.println("A adicionar participação...");
        thread.start();
    }

    private static void logout() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Tem a certeza que deseja sair? (s/n)");
        String option = scanner.nextLine();
        if(option.equals("s") || option.equals("S")) {
            account = null;
            loggedIn = false;
        }
    }

    // Tools

    private static void printEventsList(List<Event> eventos) {
        System.out.println();
        for(Event event : eventos) {
            System.out.printf("Event: %-20s Type: %-10s Location: " +
                            "%-20s Date: %-12s Start Hour: %-10s End Hour: %-10s%n",
                    event.getName(), event.getType(), event.getLocation(),
                    event.getDate(), event.getStartHour(), event.getEndHour());
        }
        System.out.println();
    }

    public static int CodeGenerator() {
        int min = 100000;
        int max = 999999;

        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static boolean eventoADecorrer(Event event) {
        GregorianCalendar calendar = new GregorianCalendar();
        int currentHour = calendar.get(GregorianCalendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(GregorianCalendar.MINUTE);
        int eventStartHour = parseInt(event.getStartHour().split(":")[0]);
        int eventStartMinute = parseInt(event.getStartHour().split(":")[1]);
        int eventEndHour = parseInt(event.getEndHour().split(":")[0]);
        int eventEndMinute = parseInt(event.getEndHour().split(":")[1]);

        if(currentHour > eventStartHour && currentHour < eventEndHour)
            return false;
        else if(currentHour == eventStartHour && currentMinute >= eventStartMinute)
            return false;
        else return currentHour != eventEndHour || currentMinute > eventEndMinute;
    }

    private static void convertEventsToCsv(Event event, List<Integer> ids) {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append("Event Id; Event Name\n");
        stringbuilder.append(event.getId()).append(";").append(event.getName()).append("\n\n");
        stringbuilder.append("Lista de Participantes\n");
        stringbuilder.append("Student Number;Email\n");
        for(int id: ids) {
            stringbuilder.append(id).append(";").append(dbManager.getEmailByAccountNumber(id)).append("\n");
        }

        String path = "src/csv/relatorioPresencas.csv";
        try (FileWriter writer = new FileWriter(path)) {
            // Write the header
            writer.append(stringbuilder);
            System.out.println("Os eventos foram exportados para o ficheiro relatorioPresencas.csv");
        } catch (IOException e) {
            System.out.printf("Nao foi possivel exportar os eventos para %s" , path);
        }
    }

    private static void convertUserEventsToCsv(List<Event> events, int userId) {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append("Student Number;Email\n");
        stringbuilder.append(userId).append(";").append(dbManager.getEmailByAccountNumber(userId)).append("\n\n");
        stringbuilder.append("Nome;Local;Data;Hora de Inicio\n");
        for(Event event : events) {
            stringbuilder.append(event.getName()).append(";").append(event.getLocation()).append(";").append(event.getDate()).append(";").append(event.getStartHour()).append(";\n");
        }

        String path = "src/csv/relatorioUtilizador"+userId+".csv";
        try (FileWriter writer = new FileWriter(path)) {
            // Write the header
            writer.append(stringbuilder);
            System.out.println("Os eventos foram exportados para o ficheiro" + path);
        } catch (IOException e) {
            System.out.printf("Nao foi possivel exportar os eventos para %s" , path);
        }
    }

    private static void codeDurationDecrementer() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable senderTask = () -> {
            for(int code : dbManager.getAllCodes()) {
                int codeDuration = dbManager.getCodeDuration(code);
                if(codeDuration > 0) {
                    codeDuration--;
                    dbManager.updateCodeDuration(code, codeDuration);
                    for(ServidorBackupInterface ref: ref) {
                        try {
                            ref.updateCodeDuration(code, codeDuration);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                else {
                    if(dbManager.deleteCode(code)) {
                        for(ServidorBackupInterface ref: ref) {
                            try {
                                ref.deleteCode(code);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        System.out.println("A duração do código " + code + " expirou e foi eliminado.");
                    }
                    else
                        System.out.println("A duração do código " + code + " expirou, mas não foi possível eliminá-lo.");
                }
            }
        };
        scheduler.scheduleAtFixedRate(senderTask, 0, 1, TimeUnit.MINUTES);
    }

    //  RemoteServidor Setup

    private static void multicastThreadSetup() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable senderTask = RemoteServidor::sendHeartbeat;
        scheduler.scheduleAtFixedRate(senderTask, 0, 10, TimeUnit.SECONDS);
    }

    private static void sendHeartbeat() {
        try {
            MulticastSocket socket = new MulticastSocket();
            InetAddress group = InetAddress.getByName("230.44.44.44");
            int port = 4444;

            String heartbeatMessage = registryPort + ";" + registryServiceName + ";" + dbManager.getVersion();
            byte[] sendData = heartbeatMessage.getBytes();
            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, group, port);

            socket.send(packet);
        } catch (IOException e) {
            System.out.println("Error sending heartbeat: " + e.getMessage());
        }
    }

    private static void registerBackupCallback() {
        try {
            Registry registry = LocateRegistry.getRegistry(registryPort);
            ref.add((ServidorBackupInterface) registry.lookup(registryServiceName));
            System.out.println("Backup callback service registered.");
        } catch (Exception e) {
            System.out.println("Error registering backup callback service: " + e.getMessage());
        }
    }

    RemoteServidor() throws Exception {
        super();
    }

    @Override
    public void run() {
        try {
            dbManager = new DbManager(dbAddress, dbName);

            Registry registry = LocateRegistry.createRegistry(clientPort);
            System.out.println("Server Registry launched!");
            registry.rebind("servidor", new RemoteServidor());
            System.out.println("Server launched! Port: " + clientPort);

            Registry registryBackup = LocateRegistry.createRegistry(registryPort + 2);
            System.out.println("Backup Registry launched!");
            registryBackup.rebind("servidorBackup", new RemoteBackupServidor());
            System.out.println("Backup Server launched! Port: " + (registryPort + 2));
        } catch (Exception e) {
            System.out.println("Ocorreu um erro na inicialização do servidor ou da base de dados " + e.getMessage());
            exit(0);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.out.println("Sintaxe: RemoteServidor <listening port> <SGBD address> <BD name> <RMI Service Name> <RMI Registry Port>");
            return;
        }

        try {
            clientPort = parseInt(args[0]);
            dbAddress = args[1];
            dbName = args[2];
            registryPort = parseInt(args[4]);
            registryServiceName = args[3];
        }
        catch (Exception e) {
            System.out.println("Argumento(s) Inválido(s).");
            return;
        }

        System.out.println("Starting Server...");
        Thread thread = new Thread(new RemoteServidor());
        thread.start();
        thread.join();
        codeDurationDecrementer();
        multicastThreadSetup();

        SpringApplication.run(Application.class, args);
    }
}