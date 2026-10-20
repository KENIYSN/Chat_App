package com.chat.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.Date;

/**
 * Modèle représentant un message de chat dans MongoDB (Collection 'messages').
 *
 * <p>Cette classe gère une double compatibilité :</p>
 * <ul>
 *   <li><strong>MongoDB Atlas (BSON POJO)</strong> : Stockage avec {@link ObjectId} pour l'id,
 *       {@code senderId} (référence vers {@link User}) et {@code groupId} (référence vers {@link Group}).</li>
 *   <li><strong>WebSocket (Jackson JSON)</strong> : Maintien strict de la rétrocompatibilité
 *       avec l'application mobile React Native via le format attendu
 *       {@code { "expediteur": "...", "texte": "...", "groupe": "...", "timestamp": ... }}.</li>
 * </ul>
 *
 * <p><strong>RESTRICTION STRICTE :</strong> Aucun champ pour photos, images, icônes ou avatars.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    /** Identifiant unique MongoDB du message. */
    @BsonId
    @JsonIgnore
    private ObjectId id;

    /** Contenu textuel du message. */
    @BsonProperty("texte")
    @JsonProperty("texte")
    private String texte;

    /** Référence ObjectId vers l'utilisateur expéditeur (collection 'users'). */
    @BsonProperty("senderId")
    @JsonIgnore
    private ObjectId senderId;

    /** Référence ObjectId vers le groupe destinataire (collection 'groups'). */
    @BsonProperty("groupId")
    @JsonIgnore
    private ObjectId groupId;

    /** Nom de l'utilisateur expéditeur (dénormalisé pour performance et sérialisation WebSocket). */
    @BsonProperty("expediteur")
    @JsonProperty("expediteur")
    private String expediteur;

    /** Nom du destinataire pour un message privé (null pour les messages de groupe). */
    @BsonProperty("destinataire")
    @JsonProperty("destinataire")
    private String destinataire;

    /** Référence ObjectId vers l'utilisateur destinataire pour message privé (collection 'users'). */
    @BsonProperty("receiverId")
    @JsonIgnore
    private ObjectId receiverId;

    /** Nom du groupe / salon cible (null pour un message privé). */
    @BsonProperty("groupe")
    @JsonProperty("groupe")
    private String groupe;

    /** Date et heure d'envoi du message. */
    @BsonProperty("sentAt")
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Date sentAt;

    // ---------------------------------------------------------------
    //  Constructeurs
    // ---------------------------------------------------------------

    /** Constructeur sans argument obligatoire pour Jackson et PojoCodecProvider. */
    public Message() {
        this.id = new ObjectId();
        this.sentAt = new Date();
    }

    /**
     * Constructeur pour les messages de chat rapides / WebSocket.
     *
     * @param expediteur le nom de l'expéditeur
     * @param texte      le contenu du message
     * @param groupe     le groupe cible
     */
    public Message(String expediteur, String texte, String groupe) {
        this();
        this.expediteur = expediteur;
        this.texte = texte;
        this.groupe = groupe;
    }

    /**
     * Constructeur complet avec références NoSQL ObjectId.
     *
     * @param senderId   l'ObjectId de l'expéditeur
     * @param groupId    l'ObjectId du groupe
     * @param expediteur le pseudonyme
     * @param texte      le texte
     * @param groupe     le nom du groupe
     */
    public Message(ObjectId senderId, ObjectId groupId, String expediteur, String texte, String groupe) {
        this(expediteur, texte, groupe);
        this.senderId = senderId;
        this.groupId = groupId;
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

    public String getTexte() {
        return texte;
    }

    public void setTexte(String texte) {
        this.texte = texte;
    }

    public ObjectId getSenderId() {
        return senderId;
    }

    public void setSenderId(ObjectId senderId) {
        this.senderId = senderId;
    }

    public ObjectId getGroupId() {
        return groupId;
    }

    public void setGroupId(ObjectId groupId) {
        this.groupId = groupId;
    }

    public String getExpediteur() {
        return expediteur;
    }

    public void setExpediteur(String expediteur) {
        this.expediteur = expediteur;
    }

    public String getDestinataire() {
        return destinataire;
    }

    public void setDestinataire(String destinataire) {
        this.destinataire = destinataire;
    }

    public ObjectId getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(ObjectId receiverId) {
        this.receiverId = receiverId;
    }

    public String getGroupe() {
        return groupe;
    }

    public void setGroupe(String groupe) {
        this.groupe = groupe;
    }

    public Date getSentAt() {
        return sentAt;
    }

    public void setSentAt(Date sentAt) {
        this.sentAt = sentAt;
    }

    // ---------------------------------------------------------------
    //  toString
    // ---------------------------------------------------------------

    @Override
    public String toString() {
        return "Message{"
                + "id=" + id
                + ", expediteur='" + expediteur + '\''
                + ", groupe='" + groupe + '\''
                + ", texte='" + texte + '\''
                + ", senderId=" + senderId
                + ", groupId=" + groupId
                + ", sentAt=" + sentAt
                + '}';
    }
}
