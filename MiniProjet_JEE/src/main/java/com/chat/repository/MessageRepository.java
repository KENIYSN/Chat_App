package com.chat.repository;

import com.chat.database.MongoDBConnection;
import com.chat.model.Message;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Repository pour les opérations CRUD et d'historique sur la collection 'messages' dans MongoDB.
 */
public class MessageRepository {

    private static final Logger LOG = LoggerFactory.getLogger(MessageRepository.class);
    private static final String COLLECTION_NAME = "messages";

    private final MongoCollection<Message> collection;

    public MessageRepository() {
        MongoDBConnection conn = MongoDBConnection.getInstance();
        this.collection = conn.getCollection(COLLECTION_NAME, Message.class);
        initIndexes();
    }

    /**
     * Crée un index composite sur (groupe, sentAt) pour optimiser les requêtes d'historique.
     */
    private void initIndexes() {
        if (collection == null) return;
        try {
            collection.createIndex(Indexes.compoundIndex(
                    Indexes.ascending("groupe"),
                    Indexes.descending("sentAt")
            ));
        } catch (Exception e) {
            LOG.warn("[MESSAGE-REPO] Note lors de la création d'index : {}", e.getMessage());
        }
    }

    /**
     * Enregistre un nouveau message dans la base MongoDB Atlas.
     *
     * @param message le {@link Message} à sauvegarder
     * @return le message persisté avec son {@link ObjectId}
     */
    public Message save(Message message) {
        if (message == null) return null;

        if (message.getSentAt() == null) {
            message.setSentAt(new Date());
        }

        if (collection != null) {
            try {
                collection.insertOne(message);
                LOG.debug("💾 [MESSAGE-REPO] Message sauvegardé dans MongoDB : [{}] {} (id={})",
                        message.getGroupe(), message.getExpediteur(), message.getId());
            } catch (Exception e) {
                LOG.error("[MESSAGE-REPO] Erreur sauvegarde message : {}", e.getMessage(), e);
            }
        }
        return message;
    }

    /**
     * Récupère l'historique des derniers messages d'un groupe, triés chronologiquement (du plus ancien au plus récent).
     *
     * @param groupName le nom du groupe (ex: "general")
     * @param limit     le nombre maximum de messages récents à récupérer (ex: 50)
     * @return la liste ordonnée des messages
     */
    public List<Message> getHistoryByGroup(String groupName, int limit) {
        List<Message> messages = new ArrayList<>();
        if (collection == null || groupName == null) return messages;

        try {
            // Requête des 'limit' messages les plus récents (tri décroissant par date)
            collection.find(Filters.eq("groupe", groupName))
                    .sort(Sorts.descending("sentAt"))
                    .limit(limit)
                    .into(messages);

            // On remet la liste dans l'ordre chronologique croissant pour affichage naturel dans le chat
            Collections.reverse(messages);

            LOG.info("📜 [MESSAGE-REPO] {} messages d'historique récupérés pour le groupe '{}'",
                    messages.size(), groupName);

        } catch (Exception e) {
            LOG.error("[MESSAGE-REPO] Erreur récupération historique groupe '{}' : {}", groupName, e.getMessage());
        }

        return messages;
    }

    /**
     * Récupère l'historique des messages privés entre deux utilisateurs, triés chronologiquement.
     * Les messages privés ont le champ 'groupe' à null (ou inexistant).
     *
     * @param user1 le premier utilisateur
     * @param user2 le deuxième utilisateur
     * @param limit le nombre maximum de messages à récupérer (ex: 50)
     * @return la liste chronologique des messages privés échangés entre user1 et user2
     */
    public List<Message> getPrivateHistory(String user1, String user2, int limit) {
        List<Message> messages = new ArrayList<>();
        if (collection == null || user1 == null || user2 == null) return messages;

        try {
            // Requête : groupe est null ou inexistant, ET (exp=user1 ET dest=user2) OU (exp=user2 ET dest=user1)
            collection.find(
                    Filters.and(
                            Filters.or(
                                    Filters.eq("groupe", null),
                                    Filters.exists("groupe", false)
                            ),
                            Filters.or(
                                    Filters.and(Filters.eq("expediteur", user1), Filters.eq("destinataire", user2)),
                                    Filters.and(Filters.eq("expediteur", user2), Filters.eq("destinataire", user1))
                            )
                    )
            ).sort(Sorts.descending("sentAt"))
             .limit(limit)
             .into(messages);

            Collections.reverse(messages);

            LOG.info("💬 [MESSAGE-REPO] {} messages d'historique privé récupérés entre '{}' et '{}'",
                    messages.size(), user1, user2);

        } catch (Exception e) {
            LOG.error("[MESSAGE-REPO] Erreur récupération historique privé {} / {} : {}", user1, user2, e.getMessage());
        }

        return messages;
    }
}
