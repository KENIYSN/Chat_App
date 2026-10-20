package com.chat.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Modèle représentant un utilisateur dans la base MongoDB (Collection 'users').
 *
 * <p>Règles d'architecture :</p>
 * <ul>
 *   <li>Identifiant unique : {@link ObjectId} mappé sur {@code _id}.</li>
 *   <li>Relations : Liste de références {@link ObjectId} vers les amis (NoSQL referenced pattern).</li>
 *   <li>Restriction stricte : Aucun champ d'image, photo, icône ou avatar.</li>
 * </ul>
 */
public class User {

    /** Identifiant MongoDB unique du document. */
    @BsonId
    private ObjectId id;

    /** Nom d'utilisateur unique (indexé). */
    @BsonProperty("username")
    private String username;

    /** Adresse email de l'utilisateur. */
    @BsonProperty("email")
    private String email;

    /** Empreinte de mot de passe (haché). */
    @BsonProperty("passwordHash")
    private String passwordHash;

    /** Liste des identifiants (ObjectId) des utilisateurs amis. */
    @BsonProperty("friendsIds")
    private List<ObjectId> friendsIds = new ArrayList<>();

    /** Date et heure de création du compte utilisateur. */
    @BsonProperty("createdAt")
    private Date createdAt;

    // ---------------------------------------------------------------
    //  Constructeurs
    // ---------------------------------------------------------------

    /** Constructeur sans argument obligatoire pour le PojoCodecProvider et Jackson. */
    public User() {
        this.id = new ObjectId();
        this.createdAt = new Date();
    }

    /**
     * Constructeur pour créer un utilisateur avec son pseudonyme.
     *
     * @param username le pseudonyme
     */
    public User(String username) {
        this();
        this.username = username;
    }

    /**
     * Constructeur complet.
     *
     * @param username     le nom d'utilisateur
     * @param email        l'adresse email
     * @param passwordHash le mot de passe haché
     */
    public User(String username, String email, String passwordHash) {
        this();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPassword() {
        return passwordHash;
    }

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    public List<ObjectId> getFriendsIds() {
        if (friendsIds == null) {
            friendsIds = new ArrayList<>();
        }
        return friendsIds;
    }

    public void setFriendsIds(List<ObjectId> friendsIds) {
        this.friendsIds = friendsIds;
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
     * Ajoute un ami à la liste d'amis si non déjà présent.
     *
     * @param friendId l'identifiant de l'ami
     */
    public void addFriend(ObjectId friendId) {
        if (friendId != null && !getFriendsIds().contains(friendId)) {
            this.friendsIds.add(friendId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) || Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String toString() {
        return "User{"
                + "id=" + id
                + ", username='" + username + '\''
                + ", email='" + email + '\''
                + ", friendsCount=" + (friendsIds != null ? friendsIds.size() : 0)
                + ", createdAt=" + createdAt
                + '}';
    }
}
