package com.chat.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestionnaire Singleton de connexion à la base de données MongoDB Atlas.
 *
 * <p>
 * Fonctionnalités clés :
 * </p>
 * <ul>
 * <li>Configuration du {@link MongoClient} avec le driver officiel
 * {@code mongodb-driver-sync}.</li>
 * <li>Enregistrement du {@link PojoCodecProvider} avec mapping automatique des
 * POJOs Java.</li>
 * <li>Fermeture propre de la connexion lors de l'arrêt du serveur.</li>
 * <li>Prise en charge de la variable d'environnement {@code MONGODB_URI} ou de
 * l'URI par défaut.</li>
 * </ul>
 */
public class MongoDBConnection {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBConnection.class);

    /**
     * Chaîne de connexion MongoDB Atlas par défaut.
     * Remplacez cette valeur par votre propre URI Atlas ou définissez la variable
     * d'environnement MONGODB_URI.
     */
    public static final String DEFAULT_URI = "mongodb+srv://Yassine:conpo4-tUppox-mobkyk@keniysn.tlscqpg.mongodb.net/?appName=keniysn";

    /** Nom de la base de données de l'application. */
    public static final String DATABASE_NAME = "chat_app_db";

    /** Variable d'environnement pour l'URI MongoDB. */
    private static final String ENV_URI_KEY = "MONGODB_URI";

    /** Instance unique du Singleton. */
    private static volatile MongoDBConnection instance;

    /** Client MongoDB officiel. */
    private MongoClient mongoClient;

    /** Base de données configurée avec le CodecRegistry POJO. */
    private MongoDatabase database;

    /** Indicateur de connexion active et valide. */
    private boolean connected = false;

    /**
     * Constructeur privé pour le patron de conception Singleton.
     */
    private MongoDBConnection() {
        initialiser();
    }

    /**
     * Retourne l'instance unique du gestionnaire de connexion MongoDB.
     *
     * @return l'instance unique de {@link MongoDBConnection}
     */
    public static MongoDBConnection getInstance() {
        if (instance == null) {
            synchronized (MongoDBConnection.class) {
                if (instance == null) {
                    instance = new MongoDBConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Initialise le client MongoDB et configure le CodecRegistry POJO.
     */
    private void initialiser() {
        String envUri = System.getenv(ENV_URI_KEY);
        String uriString = (envUri != null && !envUri.isBlank()) ? envUri : DEFAULT_URI;

        // Nettoyage automatique au cas où l'utilisateur a laissé les chevrons < et >
        if (uriString.contains("<") || uriString.contains(">")) {
            uriString = uriString.replace("<", "").replace(">", "");
        }

        if (uriString.contains("[COLLE TON LIEN") || uriString.isBlank()) {
            LOG.warn("================================================================================");
            LOG.warn("⚠️  [MONGODB] L'URI MongoDB Atlas n'a pas encore été configurée !");
            LOG.warn("⚠️  Veuillez remplacer '{}' dans MongoDBConnection.java", DEFAULT_URI);
            LOG.warn("⚠️  OU définir la variable d'environnement 'MONGODB_URI'.");
            LOG.warn("⚠️  Le serveur fonctionnera en mode mémoire temporaire sans persistance Atlas.");
            LOG.warn("================================================================================");
            return;
        }

        try {
            // ── 1. Configuration du PojoCodecProvider ─────────────────────
            // automatic(true) permet de sérialiser/désérialiser automatiquement
            // toutes les classes modèles (User, Group, Message) sans configuration
            // manuelle.
            CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(
                    PojoCodecProvider.builder().automatic(true).build());

            // ── 2. Fusion avec le CodecRegistry par défaut de BSON ───────
            CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    pojoCodecRegistry);

            // ── 3. Paramétrage des réglages du client ────────────────────
            ConnectionString connectionString = new ConnectionString(uriString);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .codecRegistry(codecRegistry)
                    .applicationName("ChatAppJEE")
                    .build();

            // ── 4. Création du client et sélection de la base ─────────────
            this.mongoClient = MongoClients.create(settings);
            this.database = mongoClient.getDatabase(DATABASE_NAME);
            this.connected = true;

            LOG.info("✅ [MONGODB] Connexion à MongoDB Atlas initialisée sur la base '{}'", DATABASE_NAME);

        } catch (Exception e) {
            LOG.error("❌ [MONGODB] Échec d'initialisation de la connexion MongoDB Atlas : {}", e.getMessage(), e);
            this.connected = false;
        }
    }

    /**
     * Retourne la base de données MongoDB active configurée avec le CodecRegistry
     * POJO.
     *
     * @return {@link MongoDatabase} ou {@code null} si non connectée
     */
    public MongoDatabase getDatabase() {
        return database;
    }

    /**
     * Obtient une collection typée pour une classe modèle POJO donnée.
     *
     * @param collectionName le nom de la collection MongoDB
     * @param clazz          la classe POJO correspondante
     * @param <T>            le type générique de la classe
     * @return la {@link MongoCollection} typée ou {@code null} si non connectée
     */
    public <T> MongoCollection<T> getCollection(String collectionName, Class<T> clazz) {
        if (!connected || database == null) {
            return null;
        }
        return database.getCollection(collectionName, clazz);
    }

    /**
     * Indique si la connexion à MongoDB est active.
     *
     * @return {@code true} si connectée, sinon {@code false}
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Ferme proprement le client MongoDB et libère les ressources réseau.
     * Doit être appelée à l'arrêt du serveur dans {@code ChatServer}.
     */
    public synchronized void close() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
                connected = false;
                LOG.info("🔒 [MONGODB] Connexion MongoDB fermée avec succès.");
            } catch (Exception e) {
                LOG.warn("⚠️ [MONGODB] Erreur lors de la fermeture de la connexion : {}", e.getMessage());
            }
        }
    }
}
