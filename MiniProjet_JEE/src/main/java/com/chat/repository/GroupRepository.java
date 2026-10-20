package com.chat.repository;

import com.chat.database.MongoDBConnection;
import com.chat.model.Group;
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
 * Repository pour les opérations CRUD sur la collection 'groups' dans MongoDB.
 */
public class GroupRepository {

    private static final Logger LOG = LoggerFactory.getLogger(GroupRepository.class);
    private static final String COLLECTION_NAME = "groups";

    private final MongoCollection<Group> collection;

    public GroupRepository() {
        MongoDBConnection conn = MongoDBConnection.getInstance();
        this.collection = conn.getCollection(COLLECTION_NAME, Group.class);
        initIndexes();
    }

    /**
     * Crée un index unique sur le nom du groupe.
     */
    private void initIndexes() {
        if (collection == null) return;
        try {
            collection.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true));
        } catch (Exception e) {
            LOG.warn("[GROUP-REPO] Note lors de la création d'index : {}", e.getMessage());
        }
    }

    /**
     * Recherche un groupe par son nom unique (ex: "general").
     *
     * @param name le nom du groupe
     * @return le {@link Group} correspondant ou {@code null}
     */
    public Group findByName(String name) {
        if (collection == null || name == null) return null;
        try {
            return collection.find(Filters.eq("name", name)).first();
        } catch (Exception e) {
            LOG.error("[GROUP-REPO] Erreur recherche groupe '{}' : {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Recherche un groupe par son identifiant ObjectId.
     *
     * @param id l'identifiant MongoDB
     * @return le {@link Group} correspondant ou {@code null}
     */
    public Group findById(ObjectId id) {
        if (collection == null || id == null) return null;
        try {
            return collection.find(Filters.eq("_id", id)).first();
        } catch (Exception e) {
            LOG.error("[GROUP-REPO] Erreur recherche groupe ID '{}' : {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Récupère un groupe ou le crée automatiquement s'il n'existe pas.
     *
     * @param name le nom du salon
     * @return le {@link Group} existant ou nouvellement créé
     */
    public Group createOrGet(String name) {
        if (name == null || name.isBlank()) return null;

        Group existing = findByName(name);
        if (existing != null) {
            return existing;
        }

        Group newGroup = new Group(name);
        newGroup.setCreatedAt(new Date());

        if (collection != null) {
            try {
                collection.insertOne(newGroup);
                LOG.info("📁 [GROUP-REPO] Nouveau groupe créé dans Atlas : '{}' (id={})", name, newGroup.getId());
            } catch (Exception e) {
                // Course critique concurrente (duplicate key)
                Group fallback = findByName(name);
                if (fallback != null) return fallback;
                LOG.error("[GROUP-REPO] Erreur insertion groupe '{}' : {}", name, e.getMessage());
            }
        }
        return newGroup;
    }

    /**
     * Ajoute un membre (User) à un groupe si non déjà présent.
     *
     * @param groupId l'ID du groupe
     * @param userId  l'ID de l'utilisateur membre
     */
    public void addMember(ObjectId groupId, ObjectId userId) {
        if (collection == null || groupId == null || userId == null) return;
        try {
            collection.updateOne(Filters.eq("_id", groupId), Updates.addToSet("memberIds", userId));
        } catch (Exception e) {
            LOG.error("[GROUP-REPO] Erreur ajout membre {} dans groupe {} : {}", userId, groupId, e.getMessage());
        }
    }

    /**
     * Liste tous les groupes auxquels appartient un utilisateur.
     *
     * @param userId l'identifiant de l'utilisateur
     * @return la liste des groupes
     */
    public List<Group> listUserGroups(ObjectId userId) {
        List<Group> results = new ArrayList<>();
        if (collection == null || userId == null) return results;
        try {
            collection.find(Filters.in("memberIds", userId)).into(results);
        } catch (Exception e) {
            LOG.error("[GROUP-REPO] Erreur recherche groupes pour user {} : {}", userId, e.getMessage());
        }
        return results;
    }

    /**
     * Recherche des groupes dont le nom contient la chaîne donnée.
     *
     * @param query le terme recherché
     * @return la liste des groupes correspondants
     */
    public List<Group> searchGroups(String query) {
        List<Group> results = new ArrayList<>();
        if (collection == null || query == null || query.isBlank()) return results;

        try {
            collection.find(Filters.regex("name", ".*" + query + ".*", "i"))
                    .limit(20)
                    .into(results);
        } catch (Exception e) {
            LOG.error("[GROUP-REPO] Erreur recherche groupes '{}' : {}", query, e.getMessage());
        }
        return results;
    }

    /**
     * Retourne tous les groupes enregistrés dans MongoDB Atlas.
     *
     * @return la liste complète des groupes
     */
    public List<Group> getAllGroups() {
        List<Group> results = new ArrayList<>();
        if (collection == null) return results;

        try {
            collection.find().into(results);
        } catch (Exception e) {
            LOG.error("[GROUP-REPO] Erreur récupération tous groupes : {}", e.getMessage());
        }
        return results;
    }
}
