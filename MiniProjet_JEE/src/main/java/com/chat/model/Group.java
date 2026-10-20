package com.chat.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Modèle représentant un groupe / salon de discussion dans MongoDB (Collection 'groups').
 *
 * <p>Règles d'architecture :</p>
 * <ul>
 *   <li>Identifiant unique : {@link ObjectId} mappé sur {@code _id}.</li>
 *   <li>Relations : Liste de références {@link ObjectId} vers les membres (modèle {@link User}).</li>
 *   <li>Restriction stricte : Aucun champ d'image, photo, icône ou avatar.</li>
 * </ul>
 */
public class Group {

    /** Identifiant MongoDB unique du groupe. */
    @BsonId
    private ObjectId id;

    /** Nom du groupe (ex: "general", "dev", "design"), unique et indexé. */
    @BsonProperty("name")
    private String name;

    /** Liste des identifiants (ObjectId) des utilisateurs membres de ce groupe. */
    @BsonProperty("memberIds")
    private List<ObjectId> memberIds = new ArrayList<>();

    /** Date et heure de création du groupe. */
    @BsonProperty("createdAt")
    private Date createdAt;

    // ---------------------------------------------------------------
    //  Constructeurs
    // ---------------------------------------------------------------

    /** Constructeur sans argument obligatoire pour le PojoCodecProvider et Jackson. */
    public Group() {
        this.id = new ObjectId();
        this.createdAt = new Date();
    }

    /**
     * Constructeur pour créer un groupe avec son nom.
     *
     * @param name le nom du salon (ex: "general")
     */
    public Group(String name) {
        this();
        this.name = name;
    }

    // ---------------------------------------------------------------
    //  Getters & Setters
    // ---------------------------------------------------------------

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ObjectId> getMemberIds() {
        if (memberIds == null) {
            memberIds = new ArrayList<>();
        }
        return memberIds;
    }

    public void setMemberIds(List<ObjectId> memberIds) {
        this.memberIds = memberIds;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    // ---------------------------------------------------------------
    //  Méthodes utilitaires
    // ---------------------------------------------------------------

    /**
     * Ajoute un membre au groupe s'il n'en fait pas déjà partie.
     *
     * @param userId l'identifiant de l'utilisateur à ajouter
     */
    public void addMember(ObjectId userId) {
        if (userId != null && !getMemberIds().contains(userId)) {
            this.memberIds.add(userId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return Objects.equals(id, group.id) || Objects.equals(name, group.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Group{"
                + "id=" + id
                + ", name='" + name + '\''
                + ", memberCount=" + (memberIds != null ? memberIds.size() : 0)
                + ", createdAt=" + createdAt
                + '}';
    }
}
