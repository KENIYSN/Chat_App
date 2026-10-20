package com.chat.repository;

import com.chat.database.MongoDBConnection;
import com.chat.model.User;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Repository pour les opérations CRUD sur la collection 'users' dans MongoDB.
 */
public class UserRepository {

    private static final Logger LOG = LoggerFactory.getLogger(UserRepository.class);
    private static final String COLLECTION_NAME = "users";

    private final MongoCollection<User> collection;

    public UserRepository() {
        MongoDBConnection conn = MongoDBConnection.getInstance();
        this.collection = conn.getCollection(COLLECTION_NAME, User.class);
        initIndexes();
    }

    /**
     * Crée les index nécessaires pour garantir l'unicité du nom d'utilisateur.
     */
    private void initIndexes() {
        if (collection == null) return;
        try {
            collection.createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
        } catch (Exception e) {
            LOG.warn("[USER-REPO] Note lors de la création d'index : {}", e.getMessage());
        }
    }

    /**
     * Recherche un utilisateur par son pseudonyme.
     *
     * @param username le nom d'utilisateur
     * @return le {@link User} correspondant ou {@code null}
     */
    public User findByUsername(String username) {
        if (collection == null || username == null) return null;
        try {
            return collection.find(Filters.eq("username", username)).first();
        } catch (Exception e) {
            LOG.error("[USER-REPO] Erreur recherche username '{}' : {}", username, e.getMessage());
            return null;
        }
    }

    /**
     * Recherche un utilisateur par son identifiant ObjectId.
     *
     * @param id l'identifiant MongoDB
     * @return le {@link User} correspondant ou {@code null}
     */
    public User findById(ObjectId id) {
        if (collection == null || id == null) return null;
        try {
            return collection.find(Filters.eq("_id", id)).first();
        } catch (Exception e) {
            LOG.error("[USER-REPO] Erreur recherche ID '{}' : {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Récupère un utilisateur ou le crée automatiquement s'il n'existe pas encore.
     *
     * @param username le pseudonyme de l'utilisateur
     * @return le {@link User} existant ou nouvellement créé
     */
    public User createOrGet(String username) {
        if (username == null || username.isBlank()) return null;

        User existing = findByUsername(username);
        if (existing != null) {
            return existing;
        }

        User newUser = new User(username);
        newUser.setCreatedAt(new Date());

        if (collection != null) {
            try {
                collection.insertOne(newUser);
                LOG.info("👤 [USER-REPO] Nouvel utilisateur enregistré dans Atlas : '{}' (id={})", username, newUser.getId());
            } catch (Exception e) {
                // En cas de course critique concurrente (duplicate key)
                User fallback = findByUsername(username);
                if (fallback != null) return fallback;
                LOG.error("[USER-REPO] Erreur insertion utilisateur '{}' : {}", username, e.getMessage());
            }
        }
        return newUser;
    }

    /**
     * Enregistre un nouvel utilisateur avec nom, email, mot de passe et date de création.
     *
     * @param username le pseudonyme
     * @param email    l'adresse email
     * @param password le mot de passe
     * @return le {@link User} créé, ou null si déjà existant
     */
    public User register(String username, String email, String password) {
        if (username == null || username.isBlank()) return null;

        User existing = findByUsername(username);
        if (existing != null) {
            return null; // Pseudonyme déjà pris
        }

        User newUser = new User(username, email, password);
        newUser.setCreatedAt(new Date());

        if (collection != null) {
            try {
                collection.insertOne(newUser);
                LOG.info("👤 [USER-REPO] Compte créé dans Atlas : '{}' ({}) (id={})", username, email, newUser.getId());
                return newUser;
            } catch (Exception e) {
                LOG.error("[USER-REPO] Erreur création compte '{}' : {}", username, e.getMessage());
                return null;
            }
        }
        return newUser;
    }

    /**
     * Authentifie un utilisateur avec son nom d'utilisateur et son mot de passe.
     *
     * @param username le nom d'utilisateur
     * @param password le mot de passe
     * @return le {@link User} si les identifiants sont corrects, sinon null
     */
    public User authenticate(String username, String password) {
        if (username == null || password == null) return null;

        User user = findByUsername(username);
        if (user == null) {
            return null;
        }

        // Si l'utilisateur n'avait pas encore de mot de passe enregistré
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(password);
            if (collection != null) {
                collection.updateOne(Filters.eq("_id", user.getId()), Updates.set("passwordHash", password));
            }
            return user;
        }

        if (user.getPassword().equals(password)) {
            return user;
        }

        return null;
    }

    /**
     * Ajoute un ami à la liste d'amis d'un utilisateur.
     *
     * @param userId   l'ID de l'utilisateur principal
     * @param friendId l'ID de l'ami à lier
     */
    public void addFriend(ObjectId userId, ObjectId friendId) {
        if (collection == null || userId == null || friendId == null) return;
        try {
            collection.updateOne(Filters.eq("_id", userId), Updates.addToSet("friendsIds", friendId));
        } catch (Exception e) {
            LOG.error("[USER-REPO] Erreur ajout ami {} -> {} : {}", userId, friendId, e.getMessage());
        }
    }

    /**
     * Recherche des utilisateurs dont le nom contient la chaîne donnée.
     *
     * @param query le terme recherché
     * @return la liste des utilisateurs correspondants
     */
    public List<User> searchUsers(String query) {
        return searchUsers(query, null);
    }

    /**
     * Recherche des utilisateurs par nom en excluant l'utilisateur courant.
     *
     * @param query           le terme recherché
     * @param excludeUsername l'utilisateur à ne pas inclure dans les résultats
     * @return la liste des utilisateurs correspondants
     */
    public List<User> searchUsers(String query, String excludeUsername) {
        List<User> results = new ArrayList<>();
        if (collection == null || query == null || query.isBlank()) return results;

        try {
            if (excludeUsername != null && !excludeUsername.isBlank()) {
                collection.find(Filters.and(
                        Filters.regex("username", ".*" + query + ".*", "i"),
                        Filters.ne("username", excludeUsername)
                )).limit(20).into(results);
            } else {
                collection.find(Filters.regex("username", ".*" + query + ".*", "i"))
                        .limit(20).into(results);
            }
        } catch (Exception e) {
            LOG.error("[USER-REPO] Erreur recherche utilisateurs '{}' : {}", query, e.getMessage());
        }
        return results;
    }
}
