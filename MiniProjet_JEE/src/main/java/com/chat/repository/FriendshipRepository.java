package com.chat.repository;

import com.chat.database.MongoDBConnection;
import com.chat.model.Friendship;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository pour gérer la collection 'friendships' dans MongoDB.
 */
public class FriendshipRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FriendshipRepository.class);
    private static final String COLLECTION_NAME = "friendships";

    private final MongoCollection<Friendship> collection;

    public FriendshipRepository() {
        MongoDBConnection conn = MongoDBConnection.getInstance();
        this.collection = conn.getCollection(COLLECTION_NAME, Friendship.class);
    }

    /**
     * Envoie une demande d'amitié d'un utilisateur à un autre.
     *
     * @param sender   l'expéditeur de la demande
     * @param receiver le destinataire
     * @return true si envoyée avec succès
     */
    public boolean sendRequest(String sender, String receiver) {
        if (collection == null || sender == null || receiver == null || sender.equalsIgnoreCase(receiver)) {
            return false;
        }

        try {
            // Vérifier s'il existe déjà une relation entre les deux
            Friendship existing = collection.find(Filters.or(
                    Filters.and(Filters.eq("sender", sender), Filters.eq("receiver", receiver)),
                    Filters.and(Filters.eq("sender", receiver), Filters.eq("receiver", sender))
            )).first();

            if (existing != null) {
                // Si la relation inverse était PENDING, on l'accepte automatiquement
                if ("PENDING".equals(existing.getStatus()) && existing.getReceiver().equalsIgnoreCase(sender)) {
                    acceptRequest(existing.getSender(), sender);
                }
                return true;
            }

            Friendship request = new Friendship(sender, receiver, "PENDING");
            collection.insertOne(request);
            LOG.info("🤝 [FRIENDSHIP] Demande d'amitié envoyée : {} → {}", sender, receiver);
            return true;

        } catch (Exception e) {
            LOG.error("[FRIENDSHIP] Erreur envoi demande {} → {} : {}", sender, receiver, e.getMessage());
            return false;
        }
    }

    /**
     * Accepte une demande d'amitié.
     *
     * @param sender   l'expéditeur original de la demande
     * @param receiver l'utilisateur qui accepte
     * @return true si acceptée
     */
    public boolean acceptRequest(String sender, String receiver) {
        if (collection == null || sender == null || receiver == null) return false;

        try {
            collection.updateOne(
                    Filters.or(
                            Filters.and(Filters.eq("sender", sender), Filters.eq("receiver", receiver)),
                            Filters.and(Filters.eq("sender", receiver), Filters.eq("receiver", sender))
                    ),
                    Updates.set("status", "ACCEPTED")
            );
            LOG.info("🎉 [FRIENDSHIP] Demande d'amitié acceptée entre {} et {}", sender, receiver);
            return true;
        } catch (Exception e) {
            LOG.error("[FRIENDSHIP] Erreur acceptation demande {} / {} : {}", sender, receiver, e.getMessage());
            return false;
        }
    }

    /**
     * Refuse (supprime) une demande d'amitié en attente.
     *
     * @param sender   l'expéditeur de la demande
     * @param receiver le destinataire qui refuse
     * @return true si refusée/supprimée
     */
    public boolean rejectRequest(String sender, String receiver) {
        if (collection == null || sender == null || receiver == null) return false;

        try {
            collection.deleteMany(
                    Filters.and(
                            Filters.eq("status", "PENDING"),
                            Filters.or(
                                    Filters.and(Filters.eq("sender", sender), Filters.eq("receiver", receiver)),
                                    Filters.and(Filters.eq("sender", receiver), Filters.eq("receiver", sender))
                            )
                    )
            );
            LOG.info("❌ [FRIENDSHIP] Demande refusée / supprimée entre {} et {}", sender, receiver);
            return true;
        } catch (Exception e) {
            LOG.error("[FRIENDSHIP] Erreur refus demande {} / {} : {}", sender, receiver, e.getMessage());
            return false;
        }
    }

    /**
     * Retourne la liste des demandes d'amitié en attente reçues par un utilisateur.
     *
     * @param username le destinataire des demandes
     * @return liste des pseudonymes des expéditeurs
     */
    public List<String> getPendingRequestsReceived(String username) {
        List<String> senders = new ArrayList<>();
        if (collection == null || username == null) return senders;

        try {
            List<Friendship> list = collection.find(
                    Filters.and(
                            Filters.eq("status", "PENDING"),
                            Filters.eq("receiver", username)
                    )
            ).into(new ArrayList<>());

            for (Friendship f : list) {
                senders.add(f.getSender());
            }
        } catch (Exception e) {
            LOG.error("[FRIENDSHIP] Erreur récupération demandes en attente pour {} : {}", username, e.getMessage());
        }

        return senders;
    }

    /**
     * Retourne la liste des pseudonymes des amis acceptés d'un utilisateur.
     *
     * @param username l'utilisateur
     * @return liste des noms d'amis
     */
    public List<String> getAcceptedFriends(String username) {
        List<String> friends = new ArrayList<>();
        if (collection == null || username == null) return friends;

        try {
            List<Friendship> list = collection.find(
                    Filters.and(
                            Filters.eq("status", "ACCEPTED"),
                            Filters.or(
                                    Filters.eq("sender", username),
                                    Filters.eq("receiver", username)
                            )
                    )
            ).into(new ArrayList<>());

            for (Friendship f : list) {
                if (f.getSender().equalsIgnoreCase(username)) {
                    friends.add(f.getReceiver());
                } else {
                    friends.add(f.getSender());
                }
            }
        } catch (Exception e) {
            LOG.error("[FRIENDSHIP] Erreur récupération amis de {} : {}", username, e.getMessage());
        }

        return friends;
    }

    /**
     * Détermine le statut de relation entre deux utilisateurs.
     *
     * @param currentUser l'utilisateur connecté
     * @param otherUser   l'autre utilisateur
     * @return "FRIENDS", "REQUEST_SENT", "REQUEST_RECEIVED", ou "NONE"
     */
    public String getRelationshipStatus(String currentUser, String otherUser) {
        if (collection == null || currentUser == null || otherUser == null) return "NONE";

        try {
            Friendship f = collection.find(Filters.or(
                    Filters.and(Filters.eq("sender", currentUser), Filters.eq("receiver", otherUser)),
                    Filters.and(Filters.eq("sender", otherUser), Filters.eq("receiver", currentUser))
            )).first();

            if (f == null) return "NONE";
            if ("ACCEPTED".equals(f.getStatus())) return "FRIENDS";
            if ("PENDING".equals(f.getStatus())) {
                return f.getSender().equalsIgnoreCase(currentUser) ? "REQUEST_SENT" : "REQUEST_RECEIVED";
            }
        } catch (Exception e) {
            LOG.error("[FRIENDSHIP] Erreur statut relation : {}", e.getMessage());
        }

        return "NONE";
    }
}
