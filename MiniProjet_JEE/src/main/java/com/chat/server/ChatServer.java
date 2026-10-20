package com.chat.server;

import com.chat.controller.ChatEndpoint;
import org.glassfish.tyrus.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;

/**
 * Point d'entrée principal de l'application de Chat en Temps Réel.
 *
 * <p>Cette classe démarre un serveur WebSocket embarqué <strong>Tyrus</strong>
 * sur le port {@value #PORT}, sans nécessiter l'installation d'un serveur
 * d'application externe (Tomcat, GlassFish, WildFly…).</p>
 *
 * <h2>Comment lancer le projet :</h2>
 * <pre>
 *   # 1. Compiler et empaqueter en fat JAR
 *   mvn clean package -q
 *
 *   # 2. Démarrer le serveur
 *   java -jar target/chat-realtime-1.0-SNAPSHOT.jar
 *
 *   # --- OU directement avec Maven (sans empaqueter) ---
 *   mvn compile exec:java -Dexec.mainClass="com.chat.server.ChatServer"
 * </pre>
 *
 * <h2>Se connecter :</h2>
 * <p>Utilisez n'importe quel client WebSocket (ex : wscat, Postman, browser)
 * en ciblant l'URL :</p>
 * <pre>
 *   ws://localhost:8080/chat/{votre_pseudo}
 * </pre>
 *
 * <h2>Format de message JSON :</h2>
 * <pre>
 * {
 *   "expediteur" : "Alice",
 *   "texte"      : "Bonjour tout le monde !",
 *   "groupe"     : "general"
 * }
 * </pre>
 */
public class ChatServer {

    /** Logger SLF4J. */
    private static final Logger LOG = LoggerFactory.getLogger(ChatServer.class);

    /** Port d'écoute du serveur WebSocket. */
    private static final int PORT = 8080;

    /** Hôte sur lequel le serveur est lié (0.0.0.0 pour accepter les connexions locales et réseau). */
    private static final String HOST = "0.0.0.0";

    /** Chemin racine du contexte WebSocket. */
    private static final String CONTEXT_PATH = "/";

    // ---------------------------------------------------------------
    //  Méthode principale
    // ---------------------------------------------------------------

    /**
     * Démarre le serveur WebSocket Tyrus embarqué et l'API REST.
     * Le serveur reste actif en continu jusqu'à un arrêt explicite (SIGINT / Ctrl+C / commande 'stop').
     *
     * @param args arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {

        CountDownLatch shutdownLatch = new CountDownLatch(1);

        // ── Initialisation de la connexion MongoDB Atlas ──────────────
        com.chat.database.MongoDBConnection mongoConn = com.chat.database.MongoDBConnection.getInstance();

        // ── Démarrage du serveur REST API (Auth, Amis, Groupes) ───────
        RestApiServer restApiServer = new RestApiServer();
        restApiServer.start();

        // ── Construction du serveur Tyrus ──────────────────────────────
        Server serveur = new Server(
                HOST,
                PORT,
                CONTEXT_PATH,
                null,           // propriétés additionnelles (null = aucune)
                ChatEndpoint.class
        );

        // ── Enregistrement d'un hook d'arrêt JVM (SIGINT / CTRL+C / fermeture IDE) ──
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("[SHUTDOWN HOOK] Signal d'arrêt reçu. Fermeture ordonnée des ressources...");
            shutdownLatch.countDown();
            try {
                serveur.stop();
                LOG.info("🔒 [WEBSOCKET] Serveur Tyrus arrêté.");
            } catch (Exception ignored) {}
            try {
                restApiServer.stop();
            } catch (Exception ignored) {}
            try {
                mongoConn.close();
            } catch (Exception ignored) {}
            LOG.info("✅ Toutes les connexions et ressources ont été fermées avec succès.");
        }));

        // ── Démarrage du serveur ───────────────────────────────────────
        try {
            serveur.start();

            afficherBanniereDemarrage();

            // Écoute optionnelle de commandes sur console sans bloquer ni quitter inopinément
            Thread consoleThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim().toLowerCase();
                        if ("stop".equals(line) || "exit".equals(line) || "quit".equals(line)) {
                            LOG.info("Commande d'arrêt utilisateur reçue ('{}').", line);
                            System.exit(0);
                        }
                    }
                } catch (Exception ignored) {}
            });
            consoleThread.setDaemon(true);
            consoleThread.setName("console-reader-thread");
            consoleThread.start();

            LOG.info("🟢 Serveur actif et en attente de connexions (Tapez 'stop' ou [Ctrl+C] pour quitter)...");

            // Le thread principal reste en attente permanente
            shutdownLatch.await();

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (msg.contains("Address already in use") || msg.contains("BindException")) {
                LOG.error("❌ ERREUR DE PORT OCCUPÉ : Le port {} ou 8082 est déjà utilisé par une autre instance de serveur !", PORT);
                LOG.error("👉 Pour libérer le port sur Mac/Linux, exécutez :");
                LOG.error("     lsof -i :{} -i :8082", PORT);
                LOG.error("     kill -9 <PID>");
            } else {
                LOG.error("Erreur critique lors du démarrage du serveur : {}", msg, e);
            }
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------
    //  Méthodes utilitaires privées
    // ---------------------------------------------------------------

    /**
     * Affiche une bannière ASCII dans la console au démarrage du serveur,
     * avec les informations de connexion essentielles.
     */
    private static void afficherBanniereDemarrage() {
        String banner = """

                ╔══════════════════════════════════════════════════════════╗
                ║          💬  CHAT TEMPS RÉEL — BACKEND JEE               ║
                ║                 Serveur WebSocket Tyrus                  ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Statut   :  ✅  DÉMARRÉ AVEC SUCCÈS                     ║
                ║  Port     :  8080                                        ║
                ║  URL WS   :  ws://localhost:8080/chat/{username}         ║
                ║  Protocole:  WebSocket (Jakarta EE 10 / Tyrus 2.1)      ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Format JSON attendu :                                   ║
                ║  {                                                       ║
                ║    "expediteur" : "Alice",                               ║
                ║    "texte"      : "Bonjour !",                           ║
                ║    "groupe"     : "general"                              ║
                ║  }                                                       ║
                ╚══════════════════════════════════════════════════════════╝
                """;

        System.out.println(banner);
        LOG.info("Serveur WebSocket démarré sur ws://{}:{}/chat/{{username}}", HOST, PORT);
    }
}
