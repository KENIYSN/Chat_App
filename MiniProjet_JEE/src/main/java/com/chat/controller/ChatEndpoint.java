package com.chat.controller;

import com.chat.model.Group;
import com.chat.model.Message;
import com.chat.model.User;
import com.chat.repository.GroupRepository;
import com.chat.repository.MessageRepository;
import com.chat.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contrôleur WebSocket principal de l'application de chat.
 *
 * <p>Cette classe est annotée avec {@link ServerEndpoint} et gère
 * toutes les connexions WebSocket entrantes sur le chemin
 * {@code /chat/{username}}.</p>
 *
 * <p>Architecture :</p>
 * <ul>
 *   <li>Une {@link ConcurrentHashMap} associe chaque groupe (String)
 *       à un ensemble thread-safe de {@link Session} actives en mémoire.</li>
 *   <li>Une seconde {@link ConcurrentHashMap} associe chaque
 *       {@code sessionId} au nom d'utilisateur correspondant.</li>
 *   <li>Persistance dans <strong>MongoDB Atlas</strong> via {@link UserRepository},
 *       {@link GroupRepository} et {@link MessageRepository}.</li>
 * </ul>
 *
 * <p>Les messages reçus sont désérialisés depuis JSON via Jackson
 * ({@link ObjectMapper}), persistés dans MongoDB, puis redistribués aux membres du groupe.</p>
 */
@ServerEndpoint("/chat/{username}")
public class ChatEndpoint {

    /** Logger SLF4J. */
    private static final Logger LOG = LoggerFactory.getLogger(ChatEndpoint.class);

    /**
     * Mapper Jackson partagé (thread-safe, réutilisable).
     * Utilisé pour parser le JSON entrant et sérialiser les réponses sortantes.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Repositories MongoDB pour la persistance des données. */
    private static final UserRepository userRepo = new UserRepository();
    private static final GroupRepository groupRepo = new GroupRepository();
    private static final MessageRepository messageRepo = new MessageRepository();

    /**
     * Stockage principal en RAM pour les groupes.
     *
     * <pre>
     *   groupe  →  Set&lt;Session&gt; (sessions actives du groupe)
     * </pre>
     */
    private static final ConcurrentHashMap<String, Set<Session>> groupeSessions
            = new ConcurrentHashMap<>();

    /**
     * Index secondaire : sessionId → nom d'utilisateur.
     */
    private static final ConcurrentHashMap<String, String> sessionUtilisateurs
            = new ConcurrentHashMap<>();

    /**
     * Index par utilisateur : username → Set&lt;Session&gt;.
     * Utilisé pour acheminer les messages privés directement au bon destinataire.
     */
    private static final ConcurrentHashMap<String, Set<Session>> utilisateurSessions
            = new ConcurrentHashMap<>();

    /**
     * Indique si un utilisateur possède actuellement une session WebSocket active.
     *
     * @param username le pseudonyme à tester
     * @return true si l'utilisateur est connecté
     */
    public static boolean isUserOnline(String username) {
        if (username == null) return false;
        Set<Session> sessions = utilisateurSessions.get(username);
        return sessions != null && !sessions.isEmpty();
    }

    // ---------------------------------------------------------------
    //  @OnOpen — connexion d'un client
    // ---------------------------------------------------------------

    /**
     * Appelé automatiquement par le conteneur WebSocket quand un client
     * établit une connexion.
     *
     * @param session  la session WebSocket du client entrant
     * @param username le nom d'utilisateur passé dans l'URL
     */
    @OnOpen
    public void onOpen(Session session,
                       @PathParam("username") String username) {

        // Enregistrement de l'utilisateur dans les index en mémoire
        sessionUtilisateurs.put(session.getId(), username);
        utilisateurSessions.computeIfAbsent(username, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(session);

        // Extraire les paramètres de requête (?recipient=... pour privé ou ?group=... pour groupe)
        String qs = session.getQueryString();
        String recipient = null;
        String groupeCible = null;

        if (qs != null && !qs.isBlank()) {
            for (String param : qs.split("&")) {
                String[] parts = param.split("=");
                if (parts.length == 2) {
                    try {
                        String key = parts[0].trim();
                        String val = java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8).trim();
                        if ("recipient".equalsIgnoreCase(key)) {
                            recipient = val;
                        } else if ("group".equalsIgnoreCase(key)) {
                            groupeCible = val;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (recipient != null && !recipient.isBlank()) {
            // ── CONVERSATION PRIVÉE DIRECTE (1-TO-1) ─────────────────────
            session.getUserProperties().put("chatType", "private");
            session.getUserProperties().put("recipient", recipient);
            session.getUserProperties().remove("groupe");

            LOG.info("[CONNEXION PRIVÉE] {} (session={}) a ouvert une discussion privée avec '{}'",
                    username, session.getId(), recipient);

            // Charger l'historique privé entre les 2 utilisateurs depuis MongoDB Atlas (groupe == null)
            try {
                userRepo.createOrGet(username);
                List<Message> historiquePrive = messageRepo.getPrivateHistory(username, recipient, 50);
                for (Message m : historiquePrive) {
                    envoyerAuClient(session, m);
                }
            } catch (Exception e) {
                LOG.warn("[MONGODB] Erreur chargement historique privé pour {} / {} : {}", username, recipient, e.getMessage());
            }

            envoyerAuClient(session,
                    creerMessageSysteme("Serveur", "Discussion privée avec " + recipient, null));

        } else {
            // ── CONVERSATION DE GROUPE ────────────────────────────────────
            if (groupeCible == null || groupeCible.isBlank()) {
                groupeCible = "general";
            }
            session.getUserProperties().put("chatType", "group");
            rejoindreGroupe(session, groupeCible);

            LOG.info("[CONNEXION GROUPE] {} (session={}) a rejoint le groupe '{}'",
                    username, session.getId(), groupeCible);

            // ── Intégration MongoDB Atlas ─────────────────────────────────
            try {
                User user = userRepo.createOrGet(username);
                Group group = groupRepo.createOrGet(groupeCible);
                if (user != null && user.getId() != null && group != null && group.getId() != null) {
                    groupRepo.addMember(group.getId(), user.getId());
                }

                // Envoi de l'historique des 50 derniers messages du GROUPE CIBLÉ uniquement
                List<Message> historique = messageRepo.getHistoryByGroup(groupeCible, 50);
                for (Message msgHistorique : historique) {
                    envoyerAuClient(session, msgHistorique);
                }
            } catch (Exception e) {
                LOG.warn("[MONGODB] Note lors de l'initialisation de session pour {} : {}", username, e.getMessage());
            }

            // Notification de bienvenue au client qui vient de se connecter
            envoyerAuClient(session,
                    creerMessageSysteme("Serveur",
                            "Bienvenue " + username + " ! Vous êtes dans le groupe : #" + groupeCible,
                            groupeCible));

            // Notification aux autres membres du groupe
            diffuserDansGroupe(groupeCible, session,
                    creerMessageSysteme("Serveur",
                            username + " a rejoint le groupe #" + groupeCible,
                            groupeCible));
        }
    }

    // ---------------------------------------------------------------
    //  @OnMessage — réception d'un message
    // ---------------------------------------------------------------

    /**
     * Appelé automatiquement par le conteneur WebSocket à chaque réception
     * d'un message texte (JSON) depuis un client.
     *
     * @param jsonBrut le message JSON reçu sous forme de chaîne de caractères
     * @param session  la session WebSocket de l'expéditeur
     */
    @OnMessage
    public void onMessage(String jsonBrut, Session session) {

        Message message;
        try {
            message = MAPPER.readValue(jsonBrut, Message.class);
        } catch (IOException e) {
            LOG.error("[ERREUR PARSING] JSON invalide reçu de la session {} : {}",
                    session.getId(), jsonBrut, e);
            envoyerAuClient(session,
                    creerMessageSysteme("Serveur",
                            "Erreur : le message envoyé n'est pas un JSON valide.",
                            "erreur"));
            return;
        }

        String chatType = (String) session.getUserProperties().get("chatType");
        String destinataire = message.getDestinataire();
        if ((destinataire == null || destinataire.isBlank()) && "private".equals(chatType)) {
            destinataire = (String) session.getUserProperties().get("recipient");
        }

        boolean isPrivate = (destinataire != null && !destinataire.isBlank());

        if (isPrivate) {
            // ── CONVERSATION PRIVÉE ENTRE DEUX UTILISATEURS ─────────────
            // Le champ groupe est OBLIGATOIREMENT null dans MongoDB Atlas
            message.setGroupe(null);
            message.setGroupId(null);
            message.setDestinataire(destinataire);

            if (message.getSentAt() == null) {
                message.setSentAt(new Date());
            }

            // Sauvegarde dans MongoDB Atlas
            try {
                User expUser = userRepo.findByUsername(message.getExpediteur());
                User destUser = userRepo.findByUsername(destinataire);
                if (expUser != null) {
                    message.setSenderId(expUser.getId());
                }
                if (destUser != null) {
                    message.setReceiverId(destUser.getId());
                }
                messageRepo.save(message);
            } catch (Exception e) {
                LOG.error("[MONGODB] Erreur sauvegarde message privé : {}", e.getMessage());
            }

            LOG.info("[MSG PRIVÉ]   {} → {} : {}",
                    message.getExpediteur(), destinataire, message.getTexte());

            // Acheminement temps réel vers le destinataire s'il est connecté
            String jsonMessage = serialiserEnJson(message);
            Set<Session> destSessions = utilisateurSessions.get(destinataire);
            if (destSessions != null) {
                for (Session s : destSessions) {
                    if (s.isOpen()) {
                        envoyerTexteAsync(s, jsonMessage);
                    }
                }
            }

        } else {
            // ── CONVERSATION DE GROUPE ──────────────────────────────────
            String groupeActuel = obtenirGroupeSession(session);
            String groupeCible = (message.getGroupe() != null && !message.getGroupe().isBlank())
                    ? message.getGroupe()
                    : (groupeActuel != null ? groupeActuel : "general");

            message.setGroupe(groupeCible);
            message.setDestinataire(null);
            message.setReceiverId(null);

            // Si l'utilisateur change de groupe, le déplacer
            if (groupeActuel != null && !groupeCible.equals(groupeActuel)) {
                quitterGroupe(session, groupeActuel);
                rejoindreGroupe(session, groupeCible);
                LOG.info("[CHANGEMENT] {} a changé de groupe : '{}' → '{}'",
                        message.getExpediteur(), groupeActuel, groupeCible);
            }

            LOG.info("[MESSAGE]    [{}] {} : {}",
                    groupeCible, message.getExpediteur(), message.getTexte());

            // Sauvegarde dans MongoDB Atlas avant diffusion
            try {
                User expUser = userRepo.findByUsername(message.getExpediteur());
                if (expUser == null && message.getExpediteur() != null) {
                    expUser = userRepo.createOrGet(message.getExpediteur());
                }

                Group grp = groupRepo.findByName(groupeCible);
                if (grp == null) {
                    grp = groupRepo.createOrGet(groupeCible);
                }

                if (expUser != null) {
                    message.setSenderId(expUser.getId());
                }
                if (grp != null) {
                    message.setGroupId(grp.getId());
                }
                if (message.getSentAt() == null) {
                    message.setSentAt(new Date());
                }

                messageRepo.save(message);

            } catch (Exception e) {
                LOG.warn("[MONGODB] Erreur lors de la sauvegarde du message groupe : {}", e.getMessage());
            }

            // Diffusion du message sérialisé à tous les membres du groupe
            diffuserDansGroupeATous(groupeCible, serialiserEnJson(message));
        }
    }

    // ---------------------------------------------------------------
    //  @OnClose — déconnexion d'un client
    // ---------------------------------------------------------------

    /**
     * Appelé automatiquement par le conteneur WebSocket quand un client
     * ferme sa connexion (normalement ou anormalement).
     *
     * @param session la session WebSocket du client sortant
     */
    @OnClose
    public void onClose(Session session) {

        String username = sessionUtilisateurs.remove(session.getId());
        if (username != null) {
            Set<Session> userSess = utilisateurSessions.get(username);
            if (userSess != null) {
                userSess.remove(session);
                if (userSess.isEmpty()) {
                    utilisateurSessions.remove(username);
                }
            }
        }

        String groupe = obtenirGroupeSession(session);
        if (groupe != null) {
            quitterGroupe(session, groupe);
            LOG.info("[DÉCONNEXION] {} (session={}) a quitté le groupe '{}'",
                    username, session.getId(), groupe);

            diffuserDansGroupeATous(groupe,
                    serialiserEnJson(creerMessageSysteme("Serveur",
                            (username != null ? username : "Un utilisateur") + " a quitté le chat.",
                            groupe)));
        } else {
            LOG.info("[DÉCONNEXION] {} (session={}) a quitté la discussion privée",
                    username, session.getId());
        }
    }

    // ---------------------------------------------------------------
    //  @OnError — gestion des erreurs WebSocket
    // ---------------------------------------------------------------

    /**
     * Appelé automatiquement par le conteneur WebSocket en cas d'erreur
     * sur une session active.
     *
     * @param session   la session en erreur
     * @param throwable l'exception levée
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        String username = sessionUtilisateurs.getOrDefault(session.getId(), "inconnu");
        LOG.error("[ERREUR] Session de {} (id={}) : {}",
                username, session.getId(), throwable.getMessage(), throwable);
    }

    // ---------------------------------------------------------------
    //  Méthodes utilitaires privées
    // ---------------------------------------------------------------

    /**
     * Ajoute la session au groupe spécifié (crée le groupe si inexistant).
     *
     * @param session la session WebSocket
     * @param groupe  le nom du groupe à rejoindre
     */
    private void rejoindreGroupe(Session session, String groupe) {
        groupeSessions
                .computeIfAbsent(groupe,
                        k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(session);
        // On stocke le groupe actuel dans les propriétés utilisateur de la session
        session.getUserProperties().put("groupe", groupe);
    }

    /**
     * Retire la session du groupe spécifié.
     *
     * @param session la session WebSocket
     * @param groupe  le nom du groupe à quitter
     */
    private void quitterGroupe(Session session, String groupe) {
        Set<Session> membres = groupeSessions.get(groupe);
        if (membres != null) {
            membres.remove(session);
            // Nettoyage : on supprime l'entrée si le groupe est vide
            if (membres.isEmpty()) {
                groupeSessions.remove(groupe);
                LOG.info("[GROUPE VIDE] Suppression du groupe '{}'", groupe);
            }
        }
    }

    /**
     * Retourne le nom du groupe auquel appartient actuellement la session.
     *
     * @param session la session WebSocket
     * @return le nom du groupe, ou {@code "general"} par défaut
     */
    private String obtenirGroupeSession(Session session) {
        Object groupe = session.getUserProperties().get("groupe");
        return (groupe instanceof String s) ? s : null;
    }

    /**
     * Envoie un message JSON à tous les membres d'un groupe,
     * en excluant optionnellement l'expéditeur.
     *
     * @param groupe          le groupe cible
     * @param sessionExclue   la session à exclure (peut être {@code null})
     * @param message         le {@link Message} à envoyer
     */
    private void diffuserDansGroupe(String groupe,
                                     Session sessionExclue,
                                     Message message) {
        String json = serialiserEnJson(message);
        Set<Session> membres = groupeSessions.getOrDefault(groupe, Collections.emptySet());
        for (Session s : membres) {
            if (s.isOpen() && !s.getId().equals(
                    sessionExclue != null ? sessionExclue.getId() : "")) {
                envoyerTexteAsync(s, json);
            }
        }
    }

    /**
     * Envoie un message JSON texte à TOUS les membres d'un groupe
     * (y compris l'expéditeur, utile pour confirmer la réception).
     *
     * @param groupe   le groupe cible
     * @param jsonTexte le texte JSON à diffuser
     */
    private void diffuserDansGroupeATous(String groupe, String jsonTexte) {
        Set<Session> membres = groupeSessions.getOrDefault(groupe, Collections.emptySet());
        for (Session s : membres) {
            if (s.isOpen()) {
                envoyerTexteAsync(s, jsonTexte);
            }
        }
    }

    /**
     * Envoie un message directement à une session spécifique (envoi synchrone).
     *
     * @param session  la session destinataire
     * @param message  le {@link Message} à envoyer
     */
    private void envoyerAuClient(Session session, Message message) {
        if (session != null && session.isOpen()) {
            envoyerTexteAsync(session, serialiserEnJson(message));
        }
    }

    /**
     * Envoi asynchrone non-bloquant d'un texte brut à une session.
     *
     * @param session la session cible
     * @param texte   le texte à envoyer
     */
    private void envoyerTexteAsync(Session session, String texte) {
        session.getAsyncRemote().sendText(texte, result -> {
            if (!result.isOK()) {
                LOG.warn("[ENVOI ECHOUÉ] session={} : {}",
                        session.getId(), result.getException().getMessage());
            }
        });
    }

    /**
     * Sérialise un {@link Message} en chaîne JSON.
     * Retourne un JSON d'erreur en cas d'échec de sérialisation.
     *
     * @param message l'objet à sérialiser
     * @return la représentation JSON sous forme de String
     */
    private String serialiserEnJson(Message message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (IOException e) {
            LOG.error("[SÉRIALISATION] Impossible de convertir le message en JSON", e);
            return "{\"erreur\":\"Erreur de sérialisation interne\"}";
        }
    }

    /**
     * Crée un message système (notifications du serveur).
     *
     * @param expediteur l'expéditeur (ex : "Serveur")
     * @param texte      le contenu de la notification
     * @param groupe     le groupe concerné
     * @return un nouveau {@link Message}
     */
    private Message creerMessageSysteme(String expediteur, String texte, String groupe) {
        return new Message(expediteur, texte, groupe);
    }
}
