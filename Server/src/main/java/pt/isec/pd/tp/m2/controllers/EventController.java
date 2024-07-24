package pt.isec.pd.tp.m2.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pt.isec.pd.tp.m2.logic.DbManager;
import pt.isec.pd.tp.m2.logic.RemoteServidor;
import pt.isec.pd.tp.m2.logic.Tools.DateChecker;
import pt.isec.pd.tp.m2.logic.Tools.EmailChecker;
import pt.isec.pd.tp.m2.logic.Tools.HourChecker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import static java.lang.Integer.parseInt;
import static pt.isec.pd.tp.m2.logic.RemoteServidor.CodeGenerator;
import static pt.isec.pd.tp.m2.logic.RemoteServidor.eventoADecorrer;

@RestController
public class EventController {

    DbManager dbManager = DbManager.getInstance();

    private static final String defaultMessage = "Available options:<br>" +
            "/event/create<br>" +
            "/event/edit<br>" +
            "/event/delete<br>" +
            "/event/list/name<br>" +
            "/event/list/type<br>" +
            "/event/list/location<br>" +
            "/event/list/date<br>" +
            "/event/list/all<br>" +
            "/event/generateCode<br>" +
            "/event/checkAttendance";

    private final AtomicLong counter = new AtomicLong();

    @PostMapping("/register")
    public Event register(@RequestParam(value = "number") String number, @RequestParam(value = "email") String email,
                          @RequestParam(value = "password") String password) {
        int studentNumber;

        try {
            studentNumber = parseInt(number);
        } catch (NumberFormatException e) {
            return new Event(counter.incrementAndGet(), "Numero de estudante invalido.");
        }

        if(studentNumber > 999999999 || studentNumber < 100000000) {
            return new Event(counter.incrementAndGet(), "Numero de estudante inválido.");
        }
        if(dbManager.checkUniqueAccountNumber(studentNumber)) {
            return new Event(counter.incrementAndGet(), "Numero de estudante já registado.");
        }

        if(EmailChecker.validate(email)) {
            return new Event(counter.incrementAndGet(), "Email inválido.");
        }

        if(dbManager.checkUniqueEmail(email)) {
            return new Event(counter.incrementAndGet(), "Email já registado.");
        }

        if(password.length() < 8) {
            return new Event(counter.incrementAndGet(), "Password inválida.");
        }

        if(dbManager.registerAccount(studentNumber, email, password, "user")) {
            return new Event(counter.incrementAndGet(), "Conta registada com sucesso.");
        }

        return new Event(counter.incrementAndGet(), "Nao foi possivel criar a conta.");
    }

    @PostMapping("user/joinEvent")
    public Event joinEvent(Authentication authentication, @RequestParam(value = "code") String code) {
        int eventCode;

        String subject = authentication.getName();
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "ADMIN"))
            return new Event(counter.incrementAndGet(), "Não é possivel participar em eventos com uma conta de administrador.");

        int cc = dbManager.getAccountNumberByEmail(subject);

        try {
            eventCode = parseInt(code);
        }
        catch (NumberFormatException e) {
            return new Event(counter.incrementAndGet(), "Codigo invalido.");
        }

        pt.isec.pd.tp.m2.logic.classes.Event event = dbManager.getEventByCode(eventCode);
        if(event == null)
            return new Event(counter.incrementAndGet(), "Codigo de evento inválido.");

        List<pt.isec.pd.tp.m2.logic.classes.Event> eventsAtSameDate = dbManager.getEventsByDate(event.getDate());
        eventsAtSameDate.removeIf(RemoteServidor::eventoADecorrer);

        for(pt.isec.pd.tp.m2.logic.classes.Event e : eventsAtSameDate) {
            for(int participantId : e.getParticipants()) {
                if(participantId == cc)
                    return new Event(counter.incrementAndGet(), "Não é possivel participar em dois eventos ao mesmo tempo.");
            }
        }

        if(event.getParticipants().contains(cc))
            return new Event(counter.incrementAndGet(), "Já está inscrito neste evento.");

        if(dbManager.addParticipantToEvent(cc, event.getId()))
            return new Event(counter.incrementAndGet(), "Participante adicionado com sucesso.");

        return new Event(counter.incrementAndGet(), "Nao foi possivel adicionar o participante.");
    }

    @GetMapping("user/checkAttendance/name")
    public Event checkAttendanceByName(Authentication authentication, @RequestParam(value = "name") String name) {
        String subject = authentication.getName();
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "ADMIN"))
            return new Event(counter.incrementAndGet(), "Não é possivel participar em eventos com uma conta de administrador.");

        int cc = dbManager.getAccountNumberByEmail(subject);

        if(!dbManager.checkEventByName(name))
            return new Event(counter.incrementAndGet(), "Não existe um evento com este nome.");

        pt.isec.pd.tp.m2.logic.classes.Event event = dbManager.getEventByName(name);

        if(!event.getParticipants().contains(cc))
            return new Event(counter.incrementAndGet(), "Não participou neste evento.");
        else
            return new Event(counter.incrementAndGet(), "Presença confirmada.");
    }

    @GetMapping("user/checkAttendance/date")
    public Event checkAttendanceByDate(Authentication authentication, @RequestParam(value = "date") String date) {
        String subject = authentication.getName();
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "ADMIN"))
            return new Event(counter.incrementAndGet(), "Não é possivel participar em eventos com uma conta de administrador.");

        int cc = dbManager.getAccountNumberByEmail(subject);

        if(!dbManager.checkEventByDate(date))
            return new Event(counter.incrementAndGet(), "Não existe um evento nesta data.");

        List<pt.isec.pd.tp.m2.logic.classes.Event> events = dbManager.getEventsByDate(date);

        StringBuilder sb = new StringBuilder("Attendances:<br>");

        for(pt.isec.pd.tp.m2.logic.classes.Event event : events) {
            if(event.getParticipants().contains(cc))
                sb.append(event.getName()).append("<br>");
        }

        if(sb.toString().equals("Attendances:<br>"))
            return new Event(counter.incrementAndGet(), "Não participou em nenhum evento nesta data.");
        else
            return new Event(counter.incrementAndGet(), sb.toString());
    }

    @GetMapping("user/checkAttendance")
    public Event checkAttendence(Authentication authentication) {
        String subject = authentication.getName();
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "ADMIN"))
            return new Event(counter.incrementAndGet(), "Não é possivel participar em eventos com uma conta de administrador.");

        int cc = dbManager.getAccountNumberByEmail(subject);

        List<pt.isec.pd.tp.m2.logic.classes.Event> events = dbManager.getAllEvents();

        StringBuilder sb = new StringBuilder("Attendances:<br>");

        for(pt.isec.pd.tp.m2.logic.classes.Event event : events) {
            if(event.getParticipants().contains(cc))
                sb.append(event.getName()).append("<br>");
        }

        if(sb.toString().equals("Attendances:<br>"))
            return new Event(counter.incrementAndGet(), "Não participou em nenhum evento.");
        else
            return new Event(counter.incrementAndGet(), sb.toString());
    }

    @GetMapping("/event")
    public Event event(Authentication authentication) {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        return new Event(counter.incrementAndGet(), defaultMessage);
    }

    @PostMapping("/event/create")
    public Event createEvent(Authentication authentication, @RequestParam(value = "name") String name, @RequestParam(value = "type") String type,
                             @RequestParam(value = "location") String location, @RequestParam(value = "date") String date,
                             @RequestParam(value = "startHour") String startHour, @RequestParam(value = "endHour") String endHour)
    {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        if(dbManager.checkEventByName(name))
            return new Event(counter.incrementAndGet(), "Já existe um evento com este nome.");

        if(DateChecker.validate(date))
            return new Event(counter.incrementAndGet(), "Data inválida.");

        if (HourChecker.validate(startHour) || HourChecker.validate(endHour))
            return new Event(counter.incrementAndGet(), "Hora de inicio e/ou fim inválida.");

        if (Integer.parseInt(startHour.split(":")[0]) > Integer.parseInt(endHour.split(":")[0]))
            return new Event(counter.incrementAndGet(),"Hora inválida - Hora de fim inferior à hora de inicio do evento.");
        else if (Integer.parseInt(startHour.split(":")[0]) == Integer.parseInt(endHour.split(":")[0])) {
            if (Integer.parseInt(startHour.split(":")[1]) > Integer.parseInt(endHour.split(":")[1]))
                return new Event(counter.incrementAndGet(), "Hora inválida - Hora de fim inferior à hora de inicio do evento.");
        }

        if(dbManager.createEvent(dbManager.getHighestEventId(), name, type, location, date, startHour, endHour))
            return new Event(counter.incrementAndGet(), String.format("Event created: %s", name));
        else
            return new Event(counter.incrementAndGet(), "Erro ao criar evento.");
    }

    @DeleteMapping("/event/delete")
    public Event deleteEvent(Authentication authentication, @RequestParam(value = "name") String name)
    {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        if(!dbManager.checkEventByName(name))
            return new Event(counter.incrementAndGet(), "Não existe um evento com este nome.");

        if(dbManager.deleteEventByName(name))
            return new Event(counter.incrementAndGet(), String.format("Event deleted: %s", name));
        else
            return new Event(counter.incrementAndGet(), "Erro ao apagar evento.");
    }

    @GetMapping("event/list/name")
    public Event listEventsByName(Authentication authentication, @RequestParam(value = "name") String name) {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        if (!dbManager.checkEventByName(name))
            return new Event(counter.incrementAndGet(), "Não existe um evento com este nome.");

        List<pt.isec.pd.tp.m2.logic.classes.Event> Events = dbManager.getEventsByName(name);

        return new Event(counter.incrementAndGet(), Events.get(0).toString());
    }

    @GetMapping("event/list/type")
    public Event listEventsByTypw(Authentication authentication, @RequestParam(value = "type") String type) {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        if (!dbManager.checkEventByType(type))
            return new Event(counter.incrementAndGet(), "Não existe um evento com este tipo.");

        List<pt.isec.pd.tp.m2.logic.classes.Event> Events = dbManager.getEventsByType(type);
        StringBuilder sb = new StringBuilder();

        for(pt.isec.pd.tp.m2.logic.classes.Event event : Events)
            sb.append(event.toString()).append("<br>");

        return new Event(counter.incrementAndGet(), sb.toString());
    }

    @GetMapping("event/list/location")
    public Event listEventsByLocation(Authentication authentication, @RequestParam(value = "location") String location) {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        if (!dbManager.checkEventByLocation(location))
            return new Event(counter.incrementAndGet(), "Não existe um evento nesta localização.");

        List<pt.isec.pd.tp.m2.logic.classes.Event> Events = dbManager.getEventsByLocation(location);
        StringBuilder sb = new StringBuilder();

        for (pt.isec.pd.tp.m2.logic.classes.Event event : Events)
            sb.append(event.toString()).append("<br>");

        return new Event(counter.incrementAndGet(), sb.toString());
    }

    @GetMapping("event/list/date")
    public Event listEventsByDate(Authentication authentication, @RequestParam(value = "date") String date) {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        if (!dbManager.checkEventByDate(date))
            return new Event(counter.incrementAndGet(), "Não existe um evento nesta data.");

        List<pt.isec.pd.tp.m2.logic.classes.Event> Events = dbManager.getEventsByDate(date);
        StringBuilder sb = new StringBuilder();

        for (pt.isec.pd.tp.m2.logic.classes.Event event : Events)
            sb.append(event.toString()).append("<br>");

        return new Event(counter.incrementAndGet(), sb.toString());
    }

    @GetMapping("event/list/all")
    public Event listAllEvents(Authentication authentication) {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        List<pt.isec.pd.tp.m2.logic.classes.Event> Events = dbManager.getAllEvents();
        StringBuilder sb = new StringBuilder();

        for (pt.isec.pd.tp.m2.logic.classes.Event event : Events)
            sb.append(event.toString()).append("<br>");

        return new Event(counter.incrementAndGet(), sb.toString());
    }

    @PostMapping("event/generateCode")
    public Event generateCode(Authentication authentication, @RequestParam(value = "eventName") String name,
                              @RequestParam(value = "duration") String duration) {

        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        if (!dbManager.checkEventByName(name))
            return new Event(counter.incrementAndGet(), "Não existe um evento com este nome.");

        if (Integer.parseInt(duration) < 0)
            return new Event(counter.incrementAndGet(), "Duração inválida.");

        if(eventoADecorrer(dbManager.getEventByName(name)))
            return new Event(counter.incrementAndGet(), "Só é possivel criar códigos para eventos que estejam a decorrer.");

        int intDuration;

        try {
            intDuration = Integer.parseInt(duration);
        } catch (NumberFormatException e) {
            return new Event(counter.incrementAndGet(), "Duração inválida.");
        }

        int code = CodeGenerator();
        while(dbManager.checkUniqueEventCode(code))
            code = CodeGenerator();

        if(dbManager.createCode(code, intDuration, name))
            return new Event(counter.incrementAndGet(), String.format("Code generated: %d", code));
        else
            return new Event(counter.incrementAndGet(), "Erro ao gerar código.");
    }

    @GetMapping("event/attendances")
    //@PreAuthorize("hasAuthority('ADMIN')")
    public Event eventAttendances(Authentication authentication, @RequestParam(value = "eventName") String name) {
        Jwt userDetails = (Jwt) authentication.getPrincipal();
        String scope = userDetails.getClaim("scope");

        if(Objects.equals(scope, "USER"))
            return new Event(counter.incrementAndGet(), "Não tem acesso a este comando");

        if (!dbManager.checkEventByName(name))
            return new Event(counter.incrementAndGet(), "Não existe um evento com este nome.");

        pt.isec.pd.tp.m2.logic.classes.Event event = dbManager.getEventByName(name);
        StringBuilder sb = new StringBuilder("Attendances:<br>");

        List<Integer> ids = new ArrayList<>();

        if(event.getParticipants().isEmpty())
            return new Event(counter.incrementAndGet(), "Não existem participantes neste evento.");

        for(int cc : event.getParticipants()) {
            ids.add(cc);
            sb.append(cc).append(" - ").append(dbManager.getEmailByAccountNumber(cc)).append("<br>");
        }

        return new Event(counter.incrementAndGet(), sb.toString());
    }
}