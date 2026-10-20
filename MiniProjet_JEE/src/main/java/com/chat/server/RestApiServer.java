package com.chat.server;

import com.chat.controller.ChatEndpoint;
import com.chat.model.Group;
import com.chat.model.User;
import com.chat.repository.FriendshipRepository;
import com.chat.repository.GroupRepository;
import com.chat.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Serveur HTTP REST intégré pour l'authentification, les amitiés,
 * la recherche et la gestion des groupes via MongoDB Atlas.
 */
public class RestApiServer {

    private static final Logger LOG = LoggerFactory.getLogger(RestApiServer.class);
    private static final int HTTP_PORT = 8082;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserRepository userRepo = new UserRepository();
    private final GroupRepository groupRepo = new GroupRepository();
    private final FriendshipRepository friendshipRepo = new FriendshipRepository();

    private HttpServer server;

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            server.createContext("/api/register", new RegisterHandler());
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/home-data", new HomeDataHandler());
            server.createContext("/api/search", new SearchHandler());
            server.createContext("/api/friends/request", new FriendRequestHandler());
            server.createContext("/api/friends/accept", new FriendAcceptHandler());
            server.createContext("/api/friends/reject", new FriendRejectHandler());
            server.createContext("/api/groups/create", new GroupCreateHandler());
            server.createContext("/api/groups/join", new GroupJoinHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            LOG.info("🚀 [REST-API] Serveur HTTP REST démarré sur http://0.0.0.0:{}/api", HTTP_PORT);

        } catch (IOException e) {
            LOG.error("[REST-API] Erreur démarrage serveur HTTP REST : {}", e.getMessage(), e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOG.info("🔒 [REST-API] Serveur HTTP REST arrêté.");
        }
    }

    // ─────────────────────────────────────────────
    // Utilitaires HTTP (CORS & JSON)
    // ─────────────────────────────────────────────

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static JsonNode parseRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return MAPPER.readTree(is);
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            } else if (parts.length == 1) {
                params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), "");
            }
        }
        return params;
    }

    // ─────────────────────────────────────────────
    // Handlers
    // ─────────────────────────────────────────────

    /** 1. Inscription */
    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            JsonNode body = parseRequestBody(exchange);
            String username = body.path("username").asText("").trim();
            String email = body.path("email").asText("").trim();
            String password = body.path("password").asText("").trim();

            if (username.length() < 2 || password.length() < 3) {
                sendJsonResponse(exchange, 400, Map.of("success", false, "error", "Identifiant ou mot de passe trop court."));
                return;
            }

            User user = userRepo.register(username, email, password);
            if (user == null) {
                sendJsonResponse(exchange, 409, Map.of("success", false, "error", "Ce nom d'utilisateur est déjà utilisé."));
                return;
            }

            // Associer au groupe général par défaut
            Group general = groupRepo.createOrGet("general");
            if (general != null && user.getId() != null) {
                groupRepo.addMember(general.getId(), user.getId());
            }

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("user", Map.of(
                    "username", user.getUsername(),
                    "email", user.getEmail() != null ? user.getEmail() : "",
                    "createdAt", user.getCreatedAt().toString()
            ));
            sendJsonResponse(exchange, 201, res);
        }
    }

    /** 2. Connexion obligatoire avec Nom + Mot de passe */
    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            JsonNode body = parseRequestBody(exchange);
            String username = body.path("username").asText("").trim();
            String password = body.path("password").asText("").trim();

            if (username.isBlank() || password.isBlank()) {
                sendJsonResponse(exchange, 400, Map.of("success", false, "error", "Nom d'utilisateur et mot de passe obligatoires."));
                return;
            }

            User user = userRepo.authenticate(username, password);
            if (user == null) {
                sendJsonResponse(exchange, 401, Map.of("success", false, "error", "Nom d'utilisateur ou mot de passe incorrect."));
                return;
            }

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("user", Map.of(
                    "username", user.getUsername(),
                    "email", user.getEmail() != null ? user.getEmail() : ""
            ));
            sendJsonResponse(exchange, 200, res);
        }
    }

    /** 3. Données de l'écran d'accueil (Amis réels + Groupes rejoints) */
    private class HomeDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String username = query.get("username");

            if (username == null || username.isBlank()) {
                sendJsonResponse(exchange, 400, Map.of("error", "Paramètre username requis"));
                return;
            }

            // Récupérer les amis acceptés depuis MongoDB
            List<String> friendNames = friendshipRepo.getAcceptedFriends(username);
            List<Map<String, Object>> friendsList = new ArrayList<>();
            for (String fName : friendNames) {
                boolean online = ChatEndpoint.isUserOnline(fName);
                friendsList.add(Map.of(
                        "name", fName,
                        "online", online
                ));
            }

            // Récupérer les groupes
            User user = userRepo.findByUsername(username);
            List<Map<String, Object>> groupsList = new ArrayList<>();

            // Toujours s'assurer que "general" existe
            groupRepo.createOrGet("general");
            List<Group> allGroups = groupRepo.getAllGroups();

            for (Group g : allGroups) {
                boolean isMember = user != null && user.getId() != null
                        && g.getMemberIds() != null && g.getMemberIds().contains(user.getId());

                // On affiche general à tout le monde, et les autres groupes rejoints
                if ("general".equalsIgnoreCase(g.getName()) || isMember) {
                    groupsList.add(Map.of(
                            "id", g.getName(),
                            "name", g.getName(),
                            "isGroup", true,
                            "memberCount", g.getMemberIds() != null ? g.getMemberIds().size() : 0,
                            "isReal", true
                    ));
                }
            }

            // Récupérer les demandes d'amitié reçues en attente
            List<String> pendingRequests = friendshipRepo.getPendingRequestsReceived(username);

            sendJsonResponse(exchange, 200, Map.of(
                    "friends", friendsList,
                    "groups", groupsList,
                    "pendingRequests", pendingRequests
            ));
        }
    }

    /** 4. Recherche d'utilisateurs et de groupes dans MongoDB */
    private class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String q = query.getOrDefault("q", "").trim();
            String currentUsername = query.getOrDefault("username", "");

            if (q.isBlank()) {
                sendJsonResponse(exchange, 200, Map.of("users", List.of(), "groups", List.of()));
                return;
            }

            // Recherche des utilisateurs (hors utilisateur courant)
            List<User> foundUsers = userRepo.searchUsers(q, currentUsername);
            List<Map<String, Object>> usersResult = new ArrayList<>();
            for (User u : foundUsers) {
                String relation = friendshipRepo.getRelationshipStatus(currentUsername, u.getUsername());
                usersResult.add(Map.of(
                        "username", u.getUsername(),
                        "relationship", relation // "FRIENDS" | "REQUEST_SENT" | "REQUEST_RECEIVED" | "NONE"
                ));
            }

            // Recherche des groupes
            List<Group> foundGroups = groupRepo.searchGroups(q);
            User currentUser = userRepo.findByUsername(currentUsername);
            List<Map<String, Object>> groupsResult = new ArrayList<>();
            for (Group g : foundGroups) {
                boolean isMember = currentUser != null && currentUser.getId() != null
                        && g.getMemberIds() != null && g.getMemberIds().contains(currentUser.getId());
                groupsResult.add(Map.of(
                        "id", g.getName(),
                        "name", g.getName(),
                        "memberCount", g.getMemberIds() != null ? g.getMemberIds().size() : 0,
                        "isMember", isMember
                ));
            }

            sendJsonResponse(exchange, 200, Map.of(
                    "users", usersResult,
                    "groups", groupsResult
            ));
        }
    }

    /** 5. Demande d'amitié */
    private class FriendRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            JsonNode body = parseRequestBody(exchange);
            String sender = body.path("sender").asText("").trim();
            String receiver = body.path("receiver").asText("").trim();

            boolean ok = friendshipRepo.sendRequest(sender, receiver);
            sendJsonResponse(exchange, ok ? 200 : 400, Map.of("success", ok));
        }
    }

    /** 6. Acceptation d'amitié */
    private class FriendAcceptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            JsonNode body = parseRequestBody(exchange);
            String sender = body.path("sender").asText("").trim();
            String receiver = body.path("receiver").asText("").trim();

            boolean ok = friendshipRepo.acceptRequest(sender, receiver);
            if (ok) {
                User u1 = userRepo.findByUsername(sender);
                User u2 = userRepo.findByUsername(receiver);
                if (u1 != null && u2 != null && u1.getId() != null && u2.getId() != null) {
                    userRepo.addFriend(u1.getId(), u2.getId());
                    userRepo.addFriend(u2.getId(), u1.getId());
                }
            }
            sendJsonResponse(exchange, ok ? 200 : 400, Map.of("success", ok));
        }
    }

    /** 6b. Refus d'une demande d'amitié */
    private class FriendRejectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            JsonNode body = parseRequestBody(exchange);
            String sender = body.path("sender").asText("").trim();
            String receiver = body.path("receiver").asText("").trim();

            boolean ok = friendshipRepo.rejectRequest(sender, receiver);
            sendJsonResponse(exchange, ok ? 200 : 400, Map.of("success", ok));
        }
    }

    /** 7. Création de groupe (avec carte d'en bas) */
    private class GroupCreateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            JsonNode body = parseRequestBody(exchange);
            String name = body.path("name").asText("").trim().toLowerCase().replaceAll("\\s+", "-");
            String username = body.path("username").asText("").trim();

            if (name.length() < 2) {
                sendJsonResponse(exchange, 400, Map.of("success", false, "error", "Nom du groupe trop court."));
                return;
            }

            Group group = groupRepo.createOrGet(name);
            User creator = userRepo.findByUsername(username);
            if (creator != null && creator.getId() != null && group != null && group.getId() != null) {
                groupRepo.addMember(group.getId(), creator.getId());
            }

            sendJsonResponse(exchange, 200, Map.of(
                    "success", true,
                    "group", Map.of(
                            "id", group.getName(),
                            "name", group.getName()
                    )
            ));
        }
    }

    /** 8. Rejoindre un groupe */
    private class GroupJoinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;
            JsonNode body = parseRequestBody(exchange);
            String groupName = body.path("groupName").asText("").trim();
            String username = body.path("username").asText("").trim();

            Group group = groupRepo.findByName(groupName);
            User user = userRepo.findByUsername(username);

            if (group != null && user != null && user.getId() != null) {
                groupRepo.addMember(group.getId(), user.getId());
                sendJsonResponse(exchange, 200, Map.of("success", true));
            } else {
                sendJsonResponse(exchange, 404, Map.of("success", false, "error", "Groupe ou utilisateur introuvable"));
            }
        }
    }
}
