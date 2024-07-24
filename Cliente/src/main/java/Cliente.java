
import java.io.IOException;
import java.util.Base64;
import java.util.Scanner;
import java.net.HttpURLConnection;
import java.net.URL;


import static java.lang.System.exit;

public class Cliente {
    static String token;
    static boolean loggedIn = false;
    static boolean admin = false;

    public static String sendRequestAndShowResponse(String uri, String verb, String authorizationValue, String body) throws IOException {

        String responseBody = null;
        URL url = new URL(uri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(verb);
        connection.setRequestProperty("Accept", "application/xml, */*");

        if(authorizationValue!=null) {
            connection.setRequestProperty("Authorization", authorizationValue);
        }

        if(body!=null){
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "Application/Json");
            connection.getOutputStream().write(body.getBytes());
        }

        connection.connect();

        int responseCode = connection.getResponseCode();
        System.out.println("Response code: " +  responseCode + " (" + connection.getResponseMessage() + ")");

        Scanner s;

        if(connection.getErrorStream()!=null) {
            s = new Scanner(connection.getErrorStream()).useDelimiter("\\A");
            responseBody = s.hasNext() ? s.next() : null;
        }

        try {
            s = new Scanner(connection.getInputStream()).useDelimiter("\\A");
            responseBody = s.hasNext() ? s.next() : null;
        } catch (IOException ignored){}

        connection.disconnect();

        System.out.println(verb + " " + uri + (body==null?"":" with body: "+body) + " ==> " + responseBody);
        System.out.println();

        return responseBody;
    }

    private static void register() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o seu número de estudante:");
        String studentNumber = scanner.nextLine();
        System.out.println("Insira o seu e-mail:");
        String email = scanner.nextLine();
        System.out.println("Insira a sua password:");
        String password = scanner.nextLine();

        String body = "http://localhost:8081/register?number=" + studentNumber + "&email=" + email + "&password=" + password;
        System.out.println("A registar...");

        System.out.println(sendRequestAndShowResponse(body, "POST", null, null));
    }

    private static void login() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o seu e-mail:");
        String email = scanner.nextLine();
        System.out.println("Insira a sua password:");
        String password = scanner.nextLine();

        String body = "http://localhost:8081/login";
        System.out.println("A fazer login...");

        String conta = email + ":" + password;
        String credentials = Base64.getEncoder().encodeToString(conta.getBytes());

        token = sendRequestAndShowResponse(body, "POST", "basic "+ credentials, null);

        String[] jwtParts = token.split("\\.");
        String role = "";

        if (jwtParts.length == 3) {
            try {
                role = new String(Base64.getUrlDecoder().decode(jwtParts[1]));
            } catch (IllegalArgumentException e) {
                System.out.println("Error decoding JWT: " + e.getMessage());
            }
        }

        if (token.contains("\"status\":401")) {
            token = "";
            loggedIn = false;
            System.out.println("Credenciais inválidas.");
        }
        else {
            loggedIn = true;
            System.out.println("Login efetuado com sucesso.");
        }

        if(role.contains("\"scope\":\"ADMIN\""))
            admin = true;
    }

    private static void logout() {
        token = "";
        loggedIn = false;
        admin = false;
    }

    private static void joinEvent() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o id do evento:");
        String id = scanner.nextLine();

        String body = "http://localhost:8081/user/joinEvent?code=" + id;
        System.out.println("A registar a participação...");

        System.out.println(sendRequestAndShowResponse(body, "POST", "bearer " + token, "{\"type\":\"word\",\"length\":4}"));
    }

    private static void checkJoinedEvents() throws IOException {
        int option;

        System.out.println("Escolha o critério a utilizar:");
        System.out.println("1 - Nome");
        System.out.println("2 - Data");
        System.out.println("3 - Nenhum");

        option = new Scanner(System.in).nextInt();

        switch(option) {
            case 1:
                checkJoinedEventsByName();
                break;
            case 2:
                checkJoinedEventsByDate();
                break;
            case 3:
                checkAllJoinedEvents();
                break;
            default:
                System.out.println("Opção inválida.");
        }
    }

    private static void checkAllJoinedEvents() throws IOException {
        String body = "http://localhost:8081/user/checkAttendance";
        System.out.println("A procurar eventos...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    private static void checkJoinedEventsByDate() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira a data do evento:");
        String date = scanner.nextLine();

        String body = "http://localhost:8081/user/checkAttendance/date?date=" + date;
        System.out.println("A procurar eventos...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    private static void checkJoinedEventsByName() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o nome do evento:");
        String name = scanner.nextLine();

        String body = "http://localhost:8081/user/checkAttendance/name?name=" + name;
        System.out.println("A procurar eventos...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    private static void createEvent() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o nome do evento:");
        String name = scanner.nextLine();
        System.out.println("Insira a tipo do evento:");
        String type = scanner.nextLine();
        System.out.println("Insira a localização do evento:");
        String location = scanner.nextLine();
        System.out.println("Insira a data do evento:");
        String date = scanner.nextLine();
        System.out.println("Insira a hora de inicio do evento:");
        String startTime = scanner.nextLine();
        System.out.println("Insira a hora de fim do evento:");
        String endTime = scanner.nextLine();

        String body = "http://localhost:8081/event/create?name=" + name + "&type=" + type + "&location=" + location + "&date=" + date + "&startHour=" + startTime + "&endHour=" + endTime;
        System.out.println("A criar evento...");

        System.out.println(sendRequestAndShowResponse(body, "POST", "bearer " + token, null));
    }

    private static void removeEvent() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o nome do evento:");
        String nome = scanner.nextLine();

        String body = "http://localhost:8081/event/delete?name=" + nome;
        System.out.println("A remover evento...");

        System.out.println(sendRequestAndShowResponse(body, "DELETE", "bearer " + token, null));
    }

    private static void checkEvents() throws IOException {
        int option;

        System.out.println("Escolha o critério a utilizar:");
        System.out.println("1 - Nome");
        System.out.println("2 - Tipo");
        System.out.println("3 - Localização");
        System.out.println("4 - Data");
        System.out.println("5 - Nenhum");

        option = new Scanner(System.in).nextInt();

        switch(option) {
            case 1:
                checkEventsByName();
                break;
            case 2:
                checkEventsByType();
                break;
            case 3:
                checkEventsByLocation();
                break;
            case 4:
                checkEventsByDate();
                break;
            case 5:
                checkAllEvents();
                break;
            default:
                System.out.println("Opção inválida.");
        }
    }

    private static void checkEventsByName() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o nome do evento:");
        String name = scanner.nextLine();

        String body = "http://localhost:8081/event/list/name?name=" + name;
        System.out.println("A procurar eventos...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    private static void checkEventsByType() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o tipo do evento:");
        String type = scanner.nextLine();

        String body = "http://localhost:8081/event/list/type?type=" + type;
        System.out.println("A procurar eventos...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    private static void checkEventsByLocation() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira a localização do evento:");
        String location = scanner.nextLine();

        String body = "http://localhost:8081/event/list/location?location=" + location;
        System.out.println("A procurar eventos...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    private static void checkEventsByDate() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira a data do evento:");
        String date = scanner.nextLine();

        String body = "http://localhost:8081/event/list/date?date=" + date;
        System.out.println("A procurar eventos...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    private static void checkAllEvents() throws IOException {
        String body = "http://localhost:8081/event/list/all";
        System.out.println("A procurar eventos...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    private static void generateCode() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o nome do evento:");
        String name = scanner.nextLine();
        System.out.println("Insira a duração do código:");
        String duration = scanner.nextLine();

        String body = "http://localhost:8081/event/generateCode?eventName=" + name + "&duration=" + duration;
        System.out.println("A gerar código...");

        System.out.println(sendRequestAndShowResponse(body, "POST", "bearer " + token, null));
    }

    private static void checkAttendances() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insira o nome do evento:");
        String name = scanner.nextLine();

        String body = "http://localhost:8081/event/attendances?eventName=" + name;
        System.out.println("A procurar presenças...");

        System.out.println(sendRequestAndShowResponse(body, "GET", "bearer " + token, null));
    }

    public static void main(String[] args) throws IOException {
        int option;
        while(true) {
            if (loggedIn && !admin) {
                System.out.println("Escolha uma opção:");
                System.out.println("1 - Participar num event");
                System.out.println("2 - Ver eventos em que participa");
                System.out.println("3 - Sair");
                option = new Scanner(System.in).nextInt();

                switch (option) {
                    case 1:
                        joinEvent();
                        break;
                    case 2:
                        checkJoinedEvents();
                        break;
                    case 3:
                        logout();
                        break;
                    default:
                        System.out.println("Opção inválida.");
                }
            }
                else if(loggedIn) {
                    System.out.println("Escolha uma opção:");
                    System.out.println("1 - Criar evento");
                    System.out.println("2 - Remover Evento");
                    System.out.println("3 - Ver eventos");
                    System.out.println("4 - Gerar Código");
                    System.out.println("5 - Ver Presenças");
                    System.out.println("6 - Sair");
                    option = new Scanner(System.in).nextInt();

                    switch (option) {
                        case 1:
                            createEvent();
                            break;
                        case 2:
                            removeEvent();
                            break;
                        case 3:
                            checkEvents();
                            break;
                        case 4:
                            generateCode();
                            break;
                        case 5:
                            checkAttendances();
                            break;
                        case 6:
                            logout();
                            break;
                        default:
                            System.out.println("Opção inválida.");
                    }

                }

                else {
                System.out.println("Escolha uma opção:");
                System.out.println("1 - Registar");
                System.out.println("2 - Login");
                System.out.println("3 - Sair");
                option = new Scanner(System.in).nextInt();

                switch (option) {
                    case 1:
                        register();
                        break;
                    case 2:
                        login();
                        break;
                    case 3:
                        exit(0);
                        break;
                    default:
                        System.out.println("Opção inválida.");
                }
            }
        }
    }
}
