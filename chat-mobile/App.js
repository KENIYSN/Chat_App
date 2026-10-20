import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  StatusBar,
  Animated,
  Dimensions,
  ActivityIndicator,
  ScrollView,
  Switch,
  Modal,
  TouchableWithoutFeedback,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

const { width, height } = Dimensions.get('window');

// ─────────────────────────────────────────────
// Gestion de l'affichage plein écran
// ─────────────────────────────────────────────
const STATUS_BAR_HEIGHT = Platform.OS === 'android' ? (StatusBar.currentHeight || 28) : 0;
const BOTTOM_PADDING = Platform.OS === 'ios' ? 34 : 24;

// ─────────────────────────────────────────────
// Palette de Couleurs Moderne & Épurée (Dark Mode)
// ─────────────────────────────────────────────
const C = {
  bg: '#0F1115',              // Fond sombre pur, élégant et moderne
  card: '#1B1E26',            // Cartes, champs de saisie, surfaces secondaires
  cardHighlight: '#252936',   // Éléments actifs / surbrillance
  text: '#FFFFFF',            // Titres et textes principaux (lisibilité maximale)
  textSecondary: '#94A3B8',   // Sous-titres, dates, aperçus (gris clair net)
  muted: '#64748B',           // Placeholders et bordures discrètes
  accent: '#E0A96D',          // Couleur dorée/caramel chaleureuse
  accentDark: '#C99155',      // Accent foncé pour états pressés
  accentText: '#0F1115',      // Texte sur boutons d'accent pour un contraste optimal
  border: 'rgba(255, 255, 255, 0.08)', // Bordure subtile et élégante
  online: '#10B981',          // Indicateur vert connecté
  danger: '#EF4444',          // Rouge pour erreurs / refus
  dangerBg: 'rgba(239, 68, 68, 0.15)',
};

// ─────────────────────────────────────────────
// Configuration Backend & Base de données MongoDB Atlas
// ─────────────────────────────────────────────
const SERVER_IP = Platform.OS === 'web' ? 'localhost' : '192.168.11.110';
const API_URL = `http://${SERVER_IP}:8082/api`;
const WS_URL = `ws://${SERVER_IP}:8080/chat/`;

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────
const generateId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

const formatTime = (date) => {
  const d = date instanceof Date ? date : new Date(date);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

const getInitials = (name = '') =>
  name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2) || '?';

// ─────────────────────────────────────────────
// Composant Avatar (Initiales épurées)
// ─────────────────────────────────────────────
const Avatar = ({ name, size = 48, bg = C.cardHighlight, fg = C.accent, style }) => (
  <View style={[{
    width: size, height: size, borderRadius: size / 2,
    backgroundColor: bg, alignItems: 'center', justifyContent: 'center',
    borderWidth: 1, borderColor: C.border,
  }, style]}>
    <Text style={{ color: fg, fontWeight: '700', fontSize: size * 0.38 }}>
      {getInitials(name)}
    </Text>
  </View>
);

// ─────────────────────────────────────────────
// Composant StatusDot
// ─────────────────────────────────────────────
const StatusDot = ({ connected, size = 8 }) => {
  const pulse = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    if (!connected) return;
    const anim = Animated.loop(Animated.sequence([
      Animated.timing(pulse, { toValue: 1.4, duration: 700, useNativeDriver: true }),
      Animated.timing(pulse, { toValue: 1, duration: 700, useNativeDriver: true }),
    ]));
    anim.start();
    return () => anim.stop();
  }, [connected]);

  return (
    <Animated.View style={{
      width: size, height: size, borderRadius: size / 2,
      backgroundColor: connected ? C.online : C.danger,
      transform: connected ? [{ scale: pulse }] : [],
    }} />
  );
};

// ─────────────────────────────────────────────
// Bulle de message
// ─────────────────────────────────────────────
const MessageBubble = ({ message, isOwn }) => {
  const fade = useRef(new Animated.Value(0)).current;
  const slide = useRef(new Animated.Value(isOwn ? 20 : -20)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fade, { toValue: 1, duration: 250, useNativeDriver: true }),
      Animated.spring(slide, { toValue: 0, tension: 80, friction: 8, useNativeDriver: true }),
    ]).start();
  }, []);

  return (
    <Animated.View style={[
      styles.bubbleWrapper,
      isOwn ? styles.bubbleWrapperOwn : styles.bubbleWrapperOther,
      { opacity: fade, transform: [{ translateX: slide }] },
    ]}>
      {!isOwn && (
        <Avatar name={message.expediteur} size={32} bg={C.cardHighlight} fg={C.accent}
          style={{ marginRight: 8, marginBottom: 4 }} />
      )}
      <View style={[styles.bubble, isOwn ? styles.bubbleOwn : styles.bubbleOther]}>
        {!isOwn && (
          <Text style={styles.bubbleSender}>{message.expediteur}</Text>
        )}
        <Text style={[styles.bubbleText, isOwn && styles.bubbleTextOwn]}>
          {message.texte}
        </Text>
        <Text style={[styles.bubbleTime, isOwn && styles.bubbleTimeOwn]}>
          {formatTime(message.timestamp)}
        </Text>
      </View>
    </Animated.View>
  );
};

// ─────────────────────────────────────────────
// 1. ÉCRAN D'AUTHENTIFICATION (Nom + Mot de passe)
// ─────────────────────────────────────────────
const AuthScreen = ({ onLogin, onNavigateToRegister }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fade = useRef(new Animated.Value(0)).current;
  const slide = useRef(new Animated.Value(30)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fade, { toValue: 1, duration: 600, useNativeDriver: true }),
      Animated.spring(slide, { toValue: 0, tension: 50, friction: 9, useNativeDriver: true }),
    ]).start();
  }, []);

  const handleLogin = async () => {
    const trimmedUser = username.trim();
    const trimmedPass = password.trim();

    if (!trimmedUser || !trimmedPass) {
      setError("Le nom d'utilisateur et le mot de passe sont obligatoires.");
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await fetch(`${API_URL}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: trimmedUser, password: trimmedPass }),
      });
      const data = await res.json();

      if (res.ok && data.success) {
        onLogin(data.user.username);
      } else {
        setError(data.error || 'Erreur lors de la connexion.');
      }
    } catch (err) {
      setError('Impossible de joindre le serveur. Vérifiez votre connexion.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.authSafe}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />
      <KeyboardAvoidingView
        style={styles.authKAV}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.authScrollContent}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
        >
          <Animated.View style={[styles.authContent, { opacity: fade, transform: [{ translateY: slide }] }]}>

            <Text style={styles.authTitle}>Bienvenue</Text>
            <Text style={styles.authSub}>Connectez-vous pour échanger en direct</Text>

            {/* Nom d'utilisateur */}
            <View style={styles.fieldGroup}>
              <Text style={styles.fieldLabel}>Nom d'utilisateur</Text>
              <View style={styles.inputContainer}>
                <TextInput
                  style={styles.textInput}
                  placeholder="Ex: Alice, Bob..."
                  placeholderTextColor={C.muted}
                  value={username}
                  onChangeText={t => { setUsername(t); setError(''); }}
                  autoCapitalize="none"
                  autoCorrect={false}
                  returnKeyType="next"
                  maxLength={25}
                />
              </View>
            </View>

            {/* Mot de passe */}
            <View style={styles.fieldGroup}>
              <View style={styles.fieldLabelRow}>
                <Text style={styles.fieldLabel}>Mot de passe</Text>
              </View>
              <View style={styles.inputContainer}>
                <TextInput
                  style={styles.textInput}
                  placeholder="••••••••••••"
                  placeholderTextColor={C.muted}
                  value={password}
                  onChangeText={t => { setPassword(t); setError(''); }}
                  secureTextEntry={!showPassword}
                  returnKeyType="go"
                  onSubmitEditing={handleLogin}
                />
                <TouchableOpacity
                  onPress={() => setShowPassword(v => !v)}
                  style={styles.showPassBtn}
                  activeOpacity={0.7}
                >
                  <Text style={styles.showPassText}>{showPassword ? 'Cacher' : 'Afficher'}</Text>
                </TouchableOpacity>
              </View>
              {!!error && <Text style={styles.authError}>{error}</Text>}
            </View>

            {/* Bouton de connexion */}
            <TouchableOpacity
              style={[styles.loginBtn, (!username.trim() || !password || loading) && styles.loginBtnDisabled]}
              onPress={handleLogin}
              disabled={loading}
              activeOpacity={0.85}
            >
              {loading ? (
                <ActivityIndicator color={C.accentText} />
              ) : (
                <Text style={styles.loginBtnText}>Se connecter →</Text>
              )}
            </TouchableOpacity>

            <View style={styles.authFooter}>
              <Text style={styles.authFooterText}>Pas encore de compte ? </Text>
              <TouchableOpacity onPress={onNavigateToRegister} activeOpacity={0.7}>
                <Text style={styles.authFooterLink}>Créer un compte</Text>
              </TouchableOpacity>
            </View>

          </Animated.View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

// ─────────────────────────────────────────────
// 1b. ÉCRAN DE CRÉATION DE COMPTE (Register)
// ─────────────────────────────────────────────
const RegisterScreen = ({ onRegister, onNavigateToLogin }) => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fade = useRef(new Animated.Value(0)).current;
  const slide = useRef(new Animated.Value(30)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fade, { toValue: 1, duration: 600, useNativeDriver: true }),
      Animated.spring(slide, { toValue: 0, tension: 50, friction: 9, useNativeDriver: true }),
    ]).start();
  }, []);

  const handleRegister = async () => {
    const trimmedUser = username.trim();
    const trimmedEmail = email.trim();
    const trimmedPass = password.trim();

    if (trimmedUser.length < 2) {
      setError("L'identifiant doit contenir au moins 2 caractères.");
      return;
    }
    if (trimmedEmail && !/\S+@\S+\.\S+/.test(trimmedEmail)) {
      setError("Veuillez saisir une adresse e-mail valide.");
      return;
    }
    if (trimmedPass.length < 3) {
      setError("Le mot de passe doit contenir au moins 3 caractères.");
      return;
    }
    if (trimmedPass !== confirmPassword.trim()) {
      setError("Les deux mots de passe ne correspondent pas.");
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await fetch(`${API_URL}/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: trimmedUser,
          email: trimmedEmail,
          password: trimmedPass,
        }),
      });
      const data = await res.json();

      if (res.ok && data.success) {
        onRegister(data.user.username);
      } else {
        setError(data.error || "Erreur lors de l'inscription.");
      }
    } catch (err) {
      setError('Impossible de joindre le serveur. Vérifiez votre connexion.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.authSafe}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />
      <KeyboardAvoidingView
        style={styles.authKAV}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.authScrollContent}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
        >
          <Animated.View style={[styles.authContent, { opacity: fade, transform: [{ translateY: slide }] }]}>

            <TouchableOpacity
              onPress={onNavigateToLogin}
              style={styles.authBackBtn}
              activeOpacity={0.7}
            >
              <Text style={styles.authBackBtnText}>‹ Retour à la connexion</Text>
            </TouchableOpacity>

            <Text style={styles.authTitle}>Inscription</Text>
            <Text style={styles.authSub}>Créez votre profil pour discuter en temps réel</Text>

            {/* Nom */}
            <View style={styles.fieldGroup}>
              <Text style={styles.fieldLabel}>Nom d'utilisateur</Text>
              <View style={styles.inputContainer}>
                <TextInput
                  style={styles.textInput}
                  placeholder="Ex: Alice, Bob, Clara..."
                  placeholderTextColor={C.muted}
                  value={username}
                  onChangeText={t => { setUsername(t); setError(''); }}
                  autoCapitalize="none"
                  autoCorrect={false}
                  returnKeyType="next"
                  maxLength={25}
                />
              </View>
            </View>

            {/* Email */}
            <View style={styles.fieldGroup}>
              <Text style={styles.fieldLabel}>Adresse e-mail</Text>
              <View style={styles.inputContainer}>
                <TextInput
                  style={styles.textInput}
                  placeholder="nom@exemple.com"
                  placeholderTextColor={C.muted}
                  value={email}
                  onChangeText={t => { setEmail(t); setError(''); }}
                  autoCapitalize="none"
                  autoCorrect={false}
                  keyboardType="email-address"
                  returnKeyType="next"
                />
              </View>
            </View>

            {/* Mot de passe */}
            <View style={styles.fieldGroup}>
              <Text style={styles.fieldLabel}>Mot de passe</Text>
              <View style={styles.inputContainer}>
                <TextInput
                  style={styles.textInput}
                  placeholder="••••••••••••"
                  placeholderTextColor={C.muted}
                  value={password}
                  onChangeText={t => { setPassword(t); setError(''); }}
                  secureTextEntry={!showPassword}
                  returnKeyType="next"
                />
                <TouchableOpacity
                  onPress={() => setShowPassword(v => !v)}
                  style={styles.showPassBtn}
                  activeOpacity={0.7}
                >
                  <Text style={styles.showPassText}>{showPassword ? 'Cacher' : 'Afficher'}</Text>
                </TouchableOpacity>
              </View>
            </View>

            {/* Confirmation Mot de passe */}
            <View style={styles.fieldGroup}>
              <Text style={styles.fieldLabel}>Confirmer le mot de passe</Text>
              <View style={styles.inputContainer}>
                <TextInput
                  style={styles.textInput}
                  placeholder="••••••••••••"
                  placeholderTextColor={C.muted}
                  value={confirmPassword}
                  onChangeText={t => { setConfirmPassword(t); setError(''); }}
                  secureTextEntry={!showPassword}
                  returnKeyType="go"
                  onSubmitEditing={handleRegister}
                />
              </View>
              {!!error && <Text style={styles.authError}>{error}</Text>}
            </View>

            {/* Bouton de création */}
            <TouchableOpacity
              style={[styles.loginBtn, (!username.trim() || !password || loading) && styles.loginBtnDisabled]}
              onPress={handleRegister}
              disabled={loading}
              activeOpacity={0.85}
            >
              {loading ? (
                <ActivityIndicator color={C.accentText} />
              ) : (
                <Text style={styles.loginBtnText}>Créer mon compte →</Text>
              )}
            </TouchableOpacity>

            <View style={styles.authFooter}>
              <Text style={styles.authFooterText}>Déjà un compte ? </Text>
              <TouchableOpacity onPress={onNavigateToLogin} activeOpacity={0.7}>
                <Text style={styles.authFooterLink}>Se connecter</Text>
              </TouchableOpacity>
            </View>

          </Animated.View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

// ─────────────────────────────────────────────
// Modal Animé Bottom Sheet pour Création de Groupe
// ─────────────────────────────────────────────
const CreateGroupBottomSheet = ({ visible, onClose, onCreateGroup }) => {
  const [groupName, setGroupName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const slideAnim = useRef(new Animated.Value(height)).current;

  useEffect(() => {
    if (visible) {
      setGroupName('');
      setError('');
      Animated.spring(slideAnim, {
        toValue: 0,
        tension: 50,
        friction: 8,
        useNativeDriver: true,
      }).start();
    } else {
      Animated.timing(slideAnim, {
        toValue: height,
        duration: 250,
        useNativeDriver: true,
      }).start();
    }
  }, [visible]);

  const handleCreate = async () => {
    const trimmed = groupName.trim();
    if (trimmed.length < 2) {
      setError('Le nom du groupe doit contenir au moins 2 caractères.');
      return;
    }
    setLoading(true);
    setError('');
    const success = await onCreateGroup(trimmed);
    setLoading(false);
    if (!success) {
      setError('Erreur lors de la création du groupe.');
    }
  };

  if (!visible) return null;

  return (
    <Modal
      transparent
      visible={visible}
      animationType="fade"
      onRequestClose={onClose}
    >
      <View style={styles.bottomSheetOverlay}>
        <TouchableWithoutFeedback onPress={onClose}>
          <View style={styles.bottomSheetBackdrop} />
        </TouchableWithoutFeedback>

        <Animated.View style={[styles.bottomSheetCard, { transform: [{ translateY: slideAnim }] }]}>
          <View style={styles.bottomSheetDragHandle} />

          <View style={styles.bottomSheetHeader}>
            <View>
              <Text style={styles.bottomSheetTitle}>Nouveau Groupe</Text>
              <Text style={styles.bottomSheetSub}>Créez un canal de discussion partagé</Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.bottomSheetCloseBtn} activeOpacity={0.7}>
              <Text style={styles.bottomSheetCloseText}>✕</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.fieldGroup}>
            <Text style={styles.fieldLabel}>Nom du groupe</Text>
            <View style={styles.inputContainer}>
              <TextInput
                style={styles.textInput}
                placeholder="Ex: Projet Mobile, Gaming, Dev..."
                placeholderTextColor={C.muted}
                value={groupName}
                onChangeText={t => { setGroupName(t); setError(''); }}
                autoCapitalize="none"
                autoCorrect={false}
                returnKeyType="done"
                onSubmitEditing={handleCreate}
                maxLength={30}
              />
            </View>
            {!!error && <Text style={styles.authError}>{error}</Text>}
          </View>

          <TouchableOpacity
            style={[styles.loginBtn, (!groupName.trim() || loading) && styles.loginBtnDisabled]}
            onPress={handleCreate}
            disabled={loading}
            activeOpacity={0.85}
          >
            {loading ? (
              <ActivityIndicator color={C.accentText} />
            ) : (
              <Text style={styles.loginBtnText}>Créer le groupe</Text>
            )}
          </TouchableOpacity>
        </Animated.View>
      </View>
    </Modal>
  );
};

// ─────────────────────────────────────────────
// 2. ÉCRAN D'ACCUEIL (Home sans navbar, avec gestion des demandes)
// ─────────────────────────────────────────────
const HomeScreen = ({ pseudo, onOpenGroupChat, onOpenPrivateChat, onProfile }) => {
  const [activeTab, setActiveTab] = useState('discussions'); // 'discussions' | 'groupes'
  const [friends, setFriends] = useState([]);
  const [groups, setGroups] = useState([]);
  const [pendingRequests, setPendingRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Recherche dynamique
  const [searchText, setSearchText] = useState('');
  const [searchResults, setSearchResults] = useState({ users: [], groups: [] });
  const [searchLoading, setSearchLoading] = useState(false);

  // Bottom Sheet
  const [isBottomSheetVisible, setBottomSheetVisible] = useState(false);

  // Charger les données de la base de données
  const fetchHomeData = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/home-data?username=${encodeURIComponent(pseudo)}`);
      if (res.ok) {
        const data = await res.json();
        setFriends(data.friends || []);
        setGroups(data.groups || []);
        setPendingRequests(data.pendingRequests || []);
      }
    } catch (e) {
      console.warn('[HOME] Erreur chargement données :', e.message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [pseudo]);

  useEffect(() => {
    fetchHomeData();
    const timer = setInterval(fetchHomeData, 8000);
    return () => clearInterval(timer);
  }, [fetchHomeData]);

  // Recherche dans MongoDB par nom
  useEffect(() => {
    const q = searchText.trim();
    if (!q) {
      setSearchResults({ users: [], groups: [] });
      setSearchLoading(false);
      return;
    }

    setSearchLoading(true);
    const delayDebounce = setTimeout(async () => {
      try {
        const res = await fetch(`${API_URL}/search?q=${encodeURIComponent(q)}&username=${encodeURIComponent(pseudo)}`);
        if (res.ok) {
          const data = await res.json();
          setSearchResults(data);
        }
      } catch (err) {
        console.warn('[SEARCH] Erreur :', err.message);
      } finally {
        setSearchLoading(false);
      }
    }, 300);

    return () => clearTimeout(delayDebounce);
  }, [searchText, pseudo]);

  // Envoyer une demande d'amitié
  const handleSendFriendRequest = async (targetUsername) => {
    try {
      const res = await fetch(`${API_URL}/friends/request`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sender: pseudo, receiver: targetUsername }),
      });
      if (res.ok) {
        setSearchResults(prev => ({
          ...prev,
          users: prev.users.map(u => u.username === targetUsername ? { ...u, relationship: 'REQUEST_SENT' } : u)
        }));
      }
    } catch (_) {}
  };

  // Accepter une demande d'amitié (depuis la section d'accueil ou la recherche)
  const handleAcceptFriendRequest = async (targetUsername) => {
    try {
      const res = await fetch(`${API_URL}/friends/accept`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sender: targetUsername, receiver: pseudo }),
      });
      if (res.ok) {
        setPendingRequests(prev => prev.filter(name => name !== targetUsername));
        setSearchResults(prev => ({
          ...prev,
          users: prev.users.map(u => u.username === targetUsername ? { ...u, relationship: 'FRIENDS' } : u)
        }));
        await fetchHomeData();
      }
    } catch (_) {}
  };

  // Refuser une demande d'amitié (depuis la section d'accueil ou la recherche)
  const handleRejectFriendRequest = async (targetUsername) => {
    try {
      const res = await fetch(`${API_URL}/friends/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sender: targetUsername, receiver: pseudo }),
      });
      if (res.ok) {
        setPendingRequests(prev => prev.filter(name => name !== targetUsername));
        setSearchResults(prev => ({
          ...prev,
          users: prev.users.map(u => u.username === targetUsername ? { ...u, relationship: 'NONE' } : u)
        }));
        await fetchHomeData();
      }
    } catch (_) {}
  };

  // Création de groupe via le bottom sheet
  const handleCreateGroup = async (name) => {
    try {
      const res = await fetch(`${API_URL}/groups/create`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, username: pseudo }),
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setBottomSheetVisible(false);
        await fetchHomeData();
        onOpenGroupChat(data.group.name);
        return true;
      }
    } catch (err) {
      console.warn('[GROUP] Erreur création :', err.message);
    }
    return false;
  };

  // Rejoindre un groupe
  const handleJoinGroup = async (groupName) => {
    try {
      const res = await fetch(`${API_URL}/groups/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ groupName, username: pseudo }),
      });
      if (res.ok) {
        await fetchHomeData();
        onOpenGroupChat(groupName);
      }
    } catch (_) {}
  };

  const isSearching = searchText.trim().length > 0;
  const onlineCount = friends.filter(f => f.online).length;

  // Rendu d'un ami dans la barre horizontale supérieure -> Discussion Privée
  const renderFriend = ({ item }) => (
    <TouchableOpacity
      style={styles.friendItem}
      activeOpacity={0.8}
      onPress={() => onOpenPrivateChat(item.name)}
    >
      <View style={{ position: 'relative', marginBottom: 6 }}>
        <Avatar name={item.name} size={56} bg={C.card} fg={C.accent} />
        {item.online && <View style={styles.friendOnlineDot} />}
      </View>
      <Text style={styles.friendName} numberOfLines={1}>{item.name.split(' ')[0]}</Text>
    </TouchableOpacity>
  );

  // Rendu d'une discussion privée avec un ami dans l'onglet "Discussions"
  const renderDiscussionItem = ({ item }) => (
    <TouchableOpacity
      style={styles.convItem}
      onPress={() => onOpenPrivateChat(item.name)}
      activeOpacity={0.75}
    >
      <View style={{ position: 'relative', marginRight: 14 }}>
        <Avatar name={item.name} size={50} bg={C.cardHighlight} fg={C.accent} />
        {item.online && (
          <View style={[styles.friendOnlineDot, { bottom: 0, right: 0 }]} />
        )}
      </View>
      <View style={styles.convBody}>
        <View style={styles.convTop}>
          <Text style={styles.convName} numberOfLines={1}>{item.name}</Text>
          <Text style={styles.convTime}>{item.online ? 'En ligne' : 'Hors ligne'}</Text>
        </View>
        <View style={styles.convBottom}>
          <Text style={styles.convPreview} numberOfLines={1}>
            Discussion privée directe
          </Text>
          <View style={styles.arrowPill}>
            <Text style={styles.arrowPillText}>›</Text>
          </View>
        </View>
      </View>
    </TouchableOpacity>
  );

  // Rendu d'une conversation de groupe dans l'onglet "Groupes rejoints"
  const renderGroupItem = ({ item }) => (
    <TouchableOpacity
      style={styles.convItem}
      onPress={() => onOpenGroupChat(item.name)}
      activeOpacity={0.75}
    >
      <View style={{ position: 'relative', marginRight: 14 }}>
        <Avatar name={item.name} size={50} bg={C.cardHighlight} fg={C.accent} />
        <View style={styles.groupBadge}>
          <Text style={styles.groupBadgeText}>G</Text>
        </View>
      </View>
      <View style={styles.convBody}>
        <View style={styles.convTop}>
          <Text style={styles.convName} numberOfLines={1}>#{item.name}</Text>
          <Text style={styles.convTime}>En direct</Text>
        </View>
        <View style={styles.convBottom}>
          <Text style={styles.convPreview} numberOfLines={1}>
            {item.memberCount ? `${item.memberCount} membre(s)` : 'Discussion active'}
          </Text>
          <View style={styles.arrowPill}>
            <Text style={styles.arrowPillText}>›</Text>
          </View>
        </View>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.homeSafe}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />

      {/* En-tête : Titre & Avatar pour profil */}
      <View style={styles.homeHeader}>
        <Text style={styles.homeTitle}>Accueil</Text>
        <TouchableOpacity onPress={onProfile} activeOpacity={0.8} style={styles.profileHeaderBtn}>
          <Avatar name={pseudo} size={40} bg={C.accent} fg={C.accentText} />
        </TouchableOpacity>
      </View>

      {/* Barre de recherche dynamique MongoDB */}
      <View style={styles.searchBar}>
        <TextInput
          style={styles.searchInput}
          placeholder="Rechercher un utilisateur ou un groupe..."
          placeholderTextColor={C.muted}
          value={searchText}
          onChangeText={setSearchText}
          autoCapitalize="none"
          autoCorrect={false}
        />
        {searchLoading && <ActivityIndicator size="small" color={C.accent} style={{ marginRight: 8 }} />}
        {searchText.length > 0 && (
          <TouchableOpacity onPress={() => setSearchText('')} activeOpacity={0.7}>
            <Text style={styles.clearSearchText}>Effacer</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* VUE RECHERCHE DYNAMIQUE */}
      {isSearching ? (
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={{ paddingBottom: 60 }}
          keyboardShouldPersistTaps="handled"
        >
          {/* Résultats Utilisateurs */}
          <View style={styles.searchSectionHeader}>
            <Text style={styles.sectionTitle}>Utilisateurs ({searchResults.users.length})</Text>
          </View>

          {searchResults.users.length === 0 ? (
            <Text style={styles.emptySearchText}>Aucun utilisateur trouvé.</Text>
          ) : (
            searchResults.users.map(u => (
              <View key={u.username} style={styles.searchItem}>
                <View style={[styles.row, { flex: 1 }]}>
                  <Avatar name={u.username} size={42} bg={C.cardHighlight} fg={C.accent} style={{ marginRight: 12 }} />
                  <View>
                    <Text style={styles.searchItemName}>{u.username}</Text>
                    <Text style={styles.searchItemSub}>Utilisateur inscrit</Text>
                  </View>
                </View>

                {/* Bouton d'action selon relation */}
                {u.relationship === 'FRIENDS' && (
                  <View style={[styles.row, { gap: 8 }]}>
                    <View style={styles.friendBadge}>
                      <Text style={styles.friendBadgeText}>Ami ✓</Text>
                    </View>
                    <TouchableOpacity
                      style={styles.openBtn}
                      onPress={() => onOpenPrivateChat(u.username)}
                      activeOpacity={0.8}
                    >
                      <Text style={styles.openBtnText}>Discuter</Text>
                    </TouchableOpacity>
                  </View>
                )}
                {u.relationship === 'REQUEST_SENT' && (
                  <View style={styles.pendingBadge}>
                    <Text style={styles.pendingBadgeText}>En attente…</Text>
                  </View>
                )}
                {u.relationship === 'REQUEST_RECEIVED' && (
                  <View style={[styles.row, { gap: 6 }]}>
                    <TouchableOpacity
                      style={styles.acceptBtn}
                      onPress={() => handleAcceptFriendRequest(u.username)}
                      activeOpacity={0.8}
                    >
                      <Text style={styles.acceptBtnText}>Accepter</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.rejectBtn}
                      onPress={() => handleRejectFriendRequest(u.username)}
                      activeOpacity={0.8}
                    >
                      <Text style={styles.rejectBtnText}>Refuser</Text>
                    </TouchableOpacity>
                  </View>
                )}
                {u.relationship === 'NONE' && (
                  <TouchableOpacity
                    style={styles.addFriendBtn}
                    onPress={() => handleSendFriendRequest(u.username)}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.addFriendBtnText}>+ Demander</Text>
                  </TouchableOpacity>
                )}
              </View>
            ))
          )}

          {/* Résultats Groupes */}
          <View style={[styles.searchSectionHeader, { marginTop: 24 }]}>
            <Text style={styles.sectionTitle}>Groupes ({searchResults.groups.length})</Text>
          </View>

          {searchResults.groups.length === 0 ? (
            <Text style={styles.emptySearchText}>Aucun groupe trouvé.</Text>
          ) : (
            searchResults.groups.map(g => (
              <View key={g.name} style={styles.searchItem}>
                <View style={[styles.row, { flex: 1 }]}>
                  <Avatar name={g.name} size={42} bg={C.cardHighlight} fg={C.accent} style={{ marginRight: 12 }} />
                  <View>
                    <Text style={styles.searchItemName}>#{g.name}</Text>
                    <Text style={styles.searchItemSub}>{g.memberCount} membre(s)</Text>
                  </View>
                </View>

                {g.isMember ? (
                  <TouchableOpacity
                    style={styles.openBtn}
                    onPress={() => onOpenGroupChat(g.name)}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.openBtnText}>Ouvrir</Text>
                  </TouchableOpacity>
                ) : (
                  <TouchableOpacity
                    style={styles.joinBtn}
                    onPress={() => handleJoinGroup(g.name)}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.joinBtnText}>Rejoindre</Text>
                  </TouchableOpacity>
                )}
              </View>
            ))
          )}
        </ScrollView>
      ) : (
        /* VUE ACCUEIL NORMALE */
        <FlatList
          data={activeTab === 'discussions' ? friends : groups}
          keyExtractor={item => (item.id || item.name) + (activeTab === 'discussions' ? '-friend' : '-group')}
          renderItem={activeTab === 'discussions' ? renderDiscussionItem : renderGroupItem}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ paddingBottom: 60 }}
          refreshing={refreshing}
          onRefresh={() => { setRefreshing(true); fetchHomeData(); }}
          ListHeaderComponent={
            <>
              {/* NOUVELLE SECTION : DEMANDES D'AMITIÉ REÇUES */}
              {pendingRequests.length > 0 && (
                <View style={styles.pendingSection}>
                  <View style={styles.pendingHeader}>
                    <View style={styles.row}>
                      <Text style={styles.pendingSectionTitle}>Demandes d'amitié reçues</Text>
                      <View style={styles.pendingCountPill}>
                        <Text style={styles.pendingCountPillText}>{pendingRequests.length}</Text>
                      </View>
                    </View>
                  </View>

                  {pendingRequests.map(sender => (
                    <View key={sender} style={styles.pendingCardItem}>
                      <View style={[styles.row, { flex: 1, gap: 12 }]}>
                        <Avatar name={sender} size={44} bg={C.cardHighlight} fg={C.accent} />
                        <View style={{ flex: 1 }}>
                          <Text style={styles.pendingSenderName}>{sender}</Text>
                          <Text style={styles.pendingSenderSub}>Souhaite vous ajouter en ami</Text>
                        </View>
                      </View>

                      <View style={[styles.row, { gap: 8 }]}>
                        <TouchableOpacity
                          style={styles.acceptBtn}
                          onPress={() => handleAcceptFriendRequest(sender)}
                          activeOpacity={0.8}
                        >
                          <Text style={styles.acceptBtnText}>Accepter</Text>
                        </TouchableOpacity>

                        <TouchableOpacity
                          style={styles.rejectBtn}
                          onPress={() => handleRejectFriendRequest(sender)}
                          activeOpacity={0.8}
                        >
                          <Text style={styles.rejectBtnText}>Refuser</Text>
                        </TouchableOpacity>
                      </View>
                    </View>
                  ))}
                </View>
              )}

              {/* Section Amis */}
              <View style={styles.sectionHeader}>
                <View style={styles.row}>
                  <Text style={styles.sectionTitle}>Mes Amis ({friends.length})</Text>
                  {onlineCount > 0 && (
                    <View style={styles.onlinePill}>
                      <Text style={styles.onlinePillText}>{onlineCount} en ligne</Text>
                    </View>
                  )}
                </View>
              </View>

              {friends.length === 0 ? (
                <View style={styles.emptyFriendsCard}>
                  <Text style={styles.emptyFriendsText}>
                    Vous n'avez pas encore d'amis ajoutés.
                  </Text>
                  <Text style={styles.emptyFriendsSub}>
                    Recherchez un utilisateur ci-dessus pour lui envoyer une demande !
                  </Text>
                </View>
              ) : (
                <FlatList
                  data={friends}
                  keyExtractor={item => item.name}
                  renderItem={renderFriend}
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 22, gap: 14 }}
                />
              )}

              {/* Onglets Discussions / Groupes */}
              <View style={styles.tabBar}>
                {['discussions', 'groupes'].map(tab => (
                  <TouchableOpacity
                    key={tab}
                    style={[styles.tab, activeTab === tab && styles.tabActive]}
                    onPress={() => setActiveTab(tab)}
                    activeOpacity={0.8}
                  >
                    <Text style={[styles.tabText, activeTab === tab && styles.tabTextActive]}>
                      {tab === 'discussions' ? 'Discussions' : 'Groupes rejoints'}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </>
          }
          ListEmptyComponent={
            loading ? (
              <ActivityIndicator color={C.accent} style={{ marginTop: 40 }} />
            ) : activeTab === 'discussions' ? (
              <View style={styles.emptyGroupsCard}>
                <Text style={styles.emptyGroupsTitle}>Aucune discussion privée</Text>
                <Text style={styles.emptyGroupsSub}>
                  Ajoutez un ami avec la barre de recherche ci-dessus pour lancer une discussion privée directe !
                </Text>
              </View>
            ) : (
              <View style={styles.emptyGroupsCard}>
                <Text style={styles.emptyGroupsTitle}>Aucun groupe pour le moment</Text>
                <Text style={styles.emptyGroupsSub}>
                  Touchez le bouton (+) ci-dessous pour créer votre premier groupe !
                </Text>
              </View>
            )
          }
        />
      )}

      {/* Bouton d'action flottant (+ pour créer un groupe avec animation fluide) */}
      <TouchableOpacity
        style={styles.fab}
        onPress={() => setBottomSheetVisible(true)}
        activeOpacity={0.85}
      >
        <Text style={styles.fabText}>+</Text>
      </TouchableOpacity>

      {/* Carte d'en bas animée (Bottom Sheet) */}
      <CreateGroupBottomSheet
        visible={isBottomSheetVisible}
        onClose={() => setBottomSheetVisible(false)}
        onCreateGroup={handleCreateGroup}
      />
    </SafeAreaView>
  );
};

// ─────────────────────────────────────────────
// 3. ÉCRAN DE CHAT (Discussion de Groupe ou Discussion Privée)
// ─────────────────────────────────────────────
const ChatScreen = ({ pseudo, chatMode = 'group', targetName = 'general', onBack }) => {
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState('');
  const [wsStatus, setWsStatus] = useState('connecting');
  const wsRef = useRef(null);
  const flatListRef = useRef(null);

  // Réinitialiser les messages dès que le mode ou la cible change
  useEffect(() => {
    setMessages([]);
  }, [chatMode, targetName]);

  const connectWS = useCallback(() => {
    // Connexion WebSocket selon le mode : privé (?recipient=...) ou groupe (?group=...)
    let url;
    if (chatMode === 'private') {
      url = `${WS_URL}${encodeURIComponent(pseudo)}?recipient=${encodeURIComponent(targetName)}`;
    } else {
      url = `${WS_URL}${encodeURIComponent(pseudo)}?group=${encodeURIComponent(targetName)}`;
    }

    const ws = new WebSocket(url);
    wsRef.current = ws;
    setWsStatus('connecting');

    ws.onopen = () => setWsStatus('connected');
    ws.onerror = () => setWsStatus('error');
    ws.onclose = () => setWsStatus('disconnected');
    ws.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data);

        // Filtre selon le mode de discussion
        if (chatMode === 'private') {
          // Un message privé ne doit pas appartenir à un groupe
          if (data.groupe) return;

          // Vérifier que le message concerne bien notre échange privé
          if (data.expediteur !== 'Serveur') {
            const isOurConvo =
              (data.expediteur === pseudo && data.destinataire === targetName) ||
              (data.expediteur === targetName && data.destinataire === pseudo);
            if (!isOurConvo) return;
          }
        } else {
          // En mode groupe : refuser les messages qui ne correspondent pas au groupe en cours
          if (data.groupe && data.groupe !== targetName) return;
        }

        // Ignorer l'écho local de ses propres messages déjà affichés
        if (data.expediteur === pseudo && data.expediteur !== 'Serveur') return;

        setMessages(prev => [...prev, {
          id: generateId(),
          expediteur: data.expediteur || 'Inconnu',
          destinataire: data.destinataire || null,
          texte: data.texte || '',
          groupe: data.groupe || null,
          timestamp: data.timestamp ? new Date(data.timestamp) : new Date(),
        }]);
      } catch (_) { }
    };
  }, [pseudo, chatMode, targetName]);

  useEffect(() => {
    connectWS();
    return () => wsRef.current?.close();
  }, [connectWS]);

  const sendMessage = () => {
    const text = inputText.trim();
    if (!text || wsStatus !== 'connected') return;

    try {
      if (chatMode === 'private') {
        // Envoi privé : destinataire défini, et groupe STRICTEMENT null
        const payload = {
          expediteur: pseudo,
          destinataire: targetName,
          texte: text,
          groupe: null, // STRICTEMENT NULL en MongoDB Atlas !
        };
        wsRef.current.send(JSON.stringify(payload));
        setMessages(prev => [...prev, {
          id: generateId(),
          expediteur: pseudo,
          destinataire: targetName,
          texte: text,
          groupe: null,
          timestamp: new Date(),
        }]);
      } else {
        // Envoi de groupe
        const payload = {
          expediteur: pseudo,
          texte: text,
          groupe: targetName,
        };
        wsRef.current.send(JSON.stringify(payload));
        setMessages(prev => [...prev, {
          id: generateId(),
          expediteur: pseudo,
          texte: text,
          groupe: targetName,
          timestamp: new Date(),
        }]);
      }
      setInputText('');
    } catch (_) { }
  };

  useEffect(() => {
    if (messages.length > 0)
      setTimeout(() => flatListRef.current?.scrollToEnd({ animated: true }), 100);
  }, [messages]);

  const isConnected = wsStatus === 'connected';
  const statusLabel = { connecting: 'Connexion…', connected: 'En ligne', disconnected: 'Hors ligne', error: 'Erreur réseau' }[wsStatus];

  return (
    <SafeAreaView style={styles.chatSafe}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />

      {/* En-tête */}
      <View style={styles.chatHeader}>
        <TouchableOpacity style={styles.backBtn} onPress={onBack} activeOpacity={0.7}>
          <Text style={styles.backBtnText}>‹</Text>
        </TouchableOpacity>
        <View style={{ flex: 1 }}>
          <View style={[styles.row, { gap: 8 }]}>
            <Text style={styles.chatHeaderTitle}>
              {chatMode === 'private' ? targetName : `#${targetName}`}
            </Text>
            <StatusDot connected={isConnected} />
          </View>
          <Text style={styles.chatHeaderSub}>
            {chatMode === 'private' ? `Discussion privée • ${statusLabel}` : statusLabel}
          </Text>
        </View>
        <Avatar name={chatMode === 'private' ? targetName : pseudo} size={36} bg={C.accent} fg={C.accentText} />
      </View>

      {/* Bannières d'état */}
      {wsStatus === 'connecting' && (
        <View style={styles.infoBanner}>
          <ActivityIndicator size="small" color={C.accent} />
          <Text style={styles.infoBannerText}>
            {chatMode === 'private'
              ? `Connexion avec ${targetName}…`
              : `Connexion au canal #${targetName}…`}
          </Text>
        </View>
      )}
      {(wsStatus === 'error' || wsStatus === 'disconnected') && (
        <View style={styles.errorBanner}>
          <Text style={styles.errorBannerText}>
            {wsStatus === 'error' ? 'Erreur de connexion' : 'Connexion perdue'}
          </Text>
          <TouchableOpacity onPress={connectWS} style={styles.retryBtn} activeOpacity={0.7}>
            <Text style={styles.retryText}>Réessayer</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Messages */}
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <FlatList
          ref={flatListRef}
          data={messages}
          keyExtractor={item => item.id}
          renderItem={({ item }) => (
            <MessageBubble message={item} isOwn={item.expediteur === pseudo} />
          )}
          contentContainerStyle={styles.messageList}
          showsVerticalScrollIndicator={false}
          ListEmptyComponent={isConnected ? (
            <View style={styles.emptyChat}>
              <Text style={styles.emptyChatTitle}>
                {chatMode === 'private'
                  ? `Discussion avec ${targetName}`
                  : `Bienvenue dans #${targetName} !`}
              </Text>
              <Text style={styles.emptyChatSub}>
                {chatMode === 'private'
                  ? `Envoyez un message privé à ${targetName}. Vos échanges sont directs et confidentiels.`
                  : 'Envoyez le premier message du groupe'}
              </Text>
            </View>
          ) : null}
        />

        {/* Barre de saisie */}
        <View style={styles.inputBar}>
          <TextInput
            style={styles.chatInput}
            placeholder={chatMode === 'private' ? `Message à ${targetName}…` : 'Écrire un message…'}
            placeholderTextColor={C.muted}
            value={inputText}
            onChangeText={setInputText}
            onSubmitEditing={sendMessage}
            returnKeyType="send"
            editable={isConnected}
            multiline
            maxLength={500}
          />
          <TouchableOpacity
            style={[styles.sendBtn, (!inputText.trim() || !isConnected) && styles.sendBtnDisabled]}
            onPress={sendMessage}
            disabled={!inputText.trim() || !isConnected}
            activeOpacity={0.8}
          >
            <Text style={styles.sendIcon}>↑</Text>
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

// ─────────────────────────────────────────────
// 4. ÉCRAN PROFIL (Avec bouton de retour vers Home)
// ─────────────────────────────────────────────
const ProfileScreen = ({ pseudo, onLogout, onHome }) => {
  const [notifEnabled, setNotifEnabled] = useState(true);

  const SETTINGS = [
    { title: 'Modifier le pseudo', sub: 'Nom affiché et identifiant unique', type: 'arrow' },
    { title: 'Gérer mes groupes', sub: 'Rôles, modérations et permissions', type: 'arrow' },
    { title: 'Notifications', sub: 'Alertes sonores et vibrations', type: 'toggle' },
    { title: 'Confidentialité', sub: 'Chiffrement de bout en bout actif', type: 'arrow' },
  ];

  return (
    <SafeAreaView style={styles.profileSafe}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />

      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 60 }}
      >
        {/* En-tête avec BOUTON DE RETOUR vers l'Accueil */}
        <View style={styles.profileHeader}>
          <TouchableOpacity style={styles.backBtn} onPress={onHome} activeOpacity={0.7}>
            <Text style={styles.backBtnText}>‹</Text>
          </TouchableOpacity>
          <Text style={styles.profileHeaderTitle}>Mon Profil</Text>
          <View style={{ width: 36 }} />
        </View>

        {/* Section avatar grand format */}
        <View style={styles.profileAvatarSection}>
          <View style={{ marginBottom: 14 }}>
            <Avatar name={pseudo} size={92} bg={C.accent} fg={C.accentText} />
          </View>
          <Text style={styles.profileName}>{pseudo}</Text>
          <Text style={styles.profileHandle}>@{pseudo.toLowerCase().replace(/\s+/g, '_')}</Text>
          <View style={[styles.row, { gap: 6, marginTop: 8 }]}>
            <View style={styles.greenDot} />
            <Text style={styles.onlineLabel}>Connecté en direct</Text>
          </View>
        </View>

        {/* Paramètres épurés */}
        <View style={styles.settingsCard}>
          {SETTINGS.map((item, i) => (
            <React.Fragment key={item.title}>
              {i > 0 && <View style={styles.settingsDivider} />}
              <TouchableOpacity
                style={styles.settingsRow}
                activeOpacity={item.type === 'toggle' ? 1 : 0.7}
              >
                <View style={{ flex: 1 }}>
                  <Text style={styles.settingsTitle}>{item.title}</Text>
                  <Text style={styles.settingsSub}>{item.sub}</Text>
                </View>
                {item.type === 'arrow' && <Text style={styles.settingsArrow}>›</Text>}
                {item.type === 'toggle' && (
                  <Switch
                    value={notifEnabled}
                    onValueChange={setNotifEnabled}
                    trackColor={{ false: C.muted, true: C.accent }}
                    thumbColor={C.text}
                  />
                )}
              </TouchableOpacity>
            </React.Fragment>
          ))}
        </View>

        {/* Bouton de déconnexion */}
        <TouchableOpacity style={styles.logoutBtn} onPress={onLogout} activeOpacity={0.85}>
          <Text style={styles.logoutText}>Se déconnecter</Text>
        </TouchableOpacity>

      </ScrollView>
    </SafeAreaView>
  );
};

// ─────────────────────────────────────────────
// Composant Racine de l'Application
// ─────────────────────────────────────────────
export default function App() {
  const [screen, setScreen] = useState('auth'); // 'auth' | 'register' | 'home' | 'chat' | 'profile'
  const [pseudo, setPseudo] = useState(null);
  const [chatTarget, setChatTarget] = useState({ mode: 'group', name: 'general' });

  const handleLogin = (username) => {
    setPseudo(username);
    setScreen('home');
  };

  const handleRegister = (username) => {
    setPseudo(username);
    setScreen('home');
  };

  const handleLogout = () => {
    setPseudo(null);
    setChatTarget({ mode: 'group', name: 'general' });
    setScreen('auth');
  };

  const handleOpenGroupChat = (groupName = 'general') => {
    setChatTarget({ mode: 'group', name: groupName });
    setScreen('chat');
  };

  const handleOpenPrivateChat = (friendUsername) => {
    setChatTarget({ mode: 'private', name: friendUsername });
    setScreen('chat');
  };

  const renderCurrentScreen = () => {
    if (screen === 'auth') {
      return (
        <AuthScreen
          onLogin={handleLogin}
          onNavigateToRegister={() => setScreen('register')}
        />
      );
    }

    if (screen === 'register') {
      return (
        <RegisterScreen
          onRegister={handleRegister}
          onNavigateToLogin={() => setScreen('auth')}
        />
      );
    }

    if (screen === 'chat') {
      return (
        <ChatScreen
          pseudo={pseudo}
          chatMode={chatTarget.mode}
          targetName={chatTarget.name}
          onBack={() => setScreen('home')}
        />
      );
    }

    if (screen === 'profile') {
      return (
        <ProfileScreen
          pseudo={pseudo}
          onLogout={handleLogout}
          onHome={() => setScreen('home')}
        />
      );
    }

    return (
      <HomeScreen
        pseudo={pseudo}
        onOpenGroupChat={handleOpenGroupChat}
        onOpenPrivateChat={handleOpenPrivateChat}
        onProfile={() => setScreen('profile')}
      />
    );
  };

  return (
    <SafeAreaProvider>
      {renderCurrentScreen()}
    </SafeAreaProvider>
  );
}

// ─────────────────────────────────────────────
// Styles CSS / React Native
// ─────────────────────────────────────────────
const styles = StyleSheet.create({

  // ── Utilitaires ─────────────────────────────
  row: { flexDirection: 'row', alignItems: 'center' },

  // ── Auth & Register Screens ─────────────────
  authSafe: {
    flex: 1,
    backgroundColor: C.bg,
    paddingTop: STATUS_BAR_HEIGHT,
  },
  authKAV: {
    flex: 1,
  },
  authScrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: 28,
    paddingVertical: 24,
  },
  authContent: {
    width: '100%',
  },
  authBackBtn: {
    alignSelf: 'flex-start',
    marginBottom: 16,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: C.cardHighlight,
  },
  authBackBtnText: {
    color: C.accent,
    fontSize: 13,
    fontWeight: '600',
  },
  authTitle: {
    fontSize: 38,
    fontWeight: '800',
    color: C.text,
    textAlign: 'center',
    marginBottom: 8,
    letterSpacing: -0.5,
  },
  authSub: {
    fontSize: 14,
    color: C.textSecondary,
    textAlign: 'center',
    marginBottom: 36,
    lineHeight: 20,
  },
  fieldGroup: {
    marginBottom: 20,
  },
  fieldLabelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  fieldLabel: {
    fontSize: 13,
    color: C.textSecondary,
    fontWeight: '600',
    marginBottom: 8,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: C.card,
    borderRadius: 16,
    paddingHorizontal: 18,
    borderWidth: 1,
    borderColor: C.border,
  },
  textInput: {
    flex: 1,
    color: C.text,
    fontSize: 15,
    paddingVertical: 16,
  },
  showPassBtn: {
    paddingVertical: 8,
    paddingHorizontal: 6,
  },
  showPassText: {
    color: C.accent,
    fontSize: 13,
    fontWeight: '600',
  },
  authError: {
    color: C.danger,
    fontSize: 12,
    marginTop: 6,
    marginLeft: 4,
  },
  loginBtn: {
    backgroundColor: C.accent,
    borderRadius: 16,
    paddingVertical: 18,
    alignItems: 'center',
    marginTop: 12,
    marginBottom: 32,
    shadowColor: C.accent,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 10,
    elevation: 4,
  },
  loginBtnDisabled: {
    opacity: 0.45,
  },
  loginBtnText: {
    color: C.accentText,
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  authFooter: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  authFooterText: {
    color: C.textSecondary,
    fontSize: 13,
  },
  authFooterLink: {
    color: C.accent,
    fontSize: 13,
    fontWeight: '600',
  },

  // ── Home Screen ─────────────────────────────
  homeSafe: {
    flex: 1,
    backgroundColor: C.bg,
    paddingTop: STATUS_BAR_HEIGHT,
  },
  homeHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 14,
  },
  homeTitle: {
    fontSize: 28,
    fontWeight: '800',
    color: C.text,
    letterSpacing: -0.5,
  },
  profileHeaderBtn: {
    padding: 2,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: C.card,
    marginHorizontal: 20,
    marginBottom: 16,
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: C.border,
  },
  searchInput: {
    flex: 1,
    color: C.text,
    fontSize: 14,
  },
  clearSearchText: {
    color: C.accent,
    fontSize: 12,
    fontWeight: '600',
  },
  searchSectionHeader: {
    paddingHorizontal: 20,
    marginBottom: 10,
  },
  emptySearchText: {
    color: C.textSecondary,
    fontSize: 13,
    paddingHorizontal: 20,
    fontStyle: 'italic',
  },
  searchItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: C.card,
    marginHorizontal: 20,
    marginBottom: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: C.border,
  },
  searchItemName: {
    fontSize: 15,
    fontWeight: '700',
    color: C.text,
  },
  searchItemSub: {
    fontSize: 12,
    color: C.textSecondary,
    marginTop: 2,
  },
  addFriendBtn: {
    backgroundColor: C.accent,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 12,
  },
  addFriendBtnText: {
    color: C.accentText,
    fontSize: 12,
    fontWeight: '700',
  },
  acceptBtn: {
    backgroundColor: C.online,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 12,
  },
  acceptBtnText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  rejectBtn: {
    backgroundColor: C.dangerBg,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.35)',
  },
  rejectBtnText: {
    color: C.danger,
    fontSize: 12,
    fontWeight: '700',
  },
  pendingBadge: {
    backgroundColor: C.cardHighlight,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: C.border,
  },
  pendingBadgeText: {
    color: C.textSecondary,
    fontSize: 11,
    fontWeight: '600',
  },
  friendBadge: {
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  friendBadgeText: {
    color: C.online,
    fontSize: 11,
    fontWeight: '700',
  },
  joinBtn: {
    backgroundColor: C.accent,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 12,
  },
  joinBtnText: {
    color: C.accentText,
    fontSize: 12,
    fontWeight: '700',
  },
  openBtn: {
    backgroundColor: C.cardHighlight,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: C.border,
  },
  openBtnText: {
    color: C.accent,
    fontSize: 12,
    fontWeight: '700',
  },

  // ── Section Demandes d'amitié reçues (Home) ──
  pendingSection: {
    marginHorizontal: 20,
    marginBottom: 20,
    backgroundColor: C.card,
    borderRadius: 20,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(224, 169, 109, 0.3)',
  },
  pendingHeader: {
    marginBottom: 12,
  },
  pendingSectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: C.accent,
    marginRight: 8,
  },
  pendingCountPill: {
    backgroundColor: C.accent,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  pendingCountPillText: {
    color: C.accentText,
    fontSize: 11,
    fontWeight: '800',
  },
  pendingCardItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: C.border,
  },
  pendingSenderName: {
    fontSize: 15,
    fontWeight: '700',
    color: C.text,
  },
  pendingSenderSub: {
    fontSize: 12,
    color: C.textSecondary,
    marginTop: 2,
  },

  // Section Mes Amis
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: C.text,
    marginRight: 10,
  },
  onlinePill: {
    backgroundColor: C.cardHighlight,
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderWidth: 1,
    borderColor: C.border,
  },
  onlinePillText: {
    color: C.accent,
    fontSize: 11,
    fontWeight: '600',
  },
  emptyFriendsCard: {
    marginHorizontal: 20,
    backgroundColor: C.card,
    borderRadius: 16,
    padding: 16,
    marginBottom: 18,
    borderWidth: 1,
    borderColor: C.border,
  },
  emptyFriendsText: {
    color: C.text,
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 4,
  },
  emptyFriendsSub: {
    color: C.textSecondary,
    fontSize: 12,
  },
  friendItem: {
    alignItems: 'center',
    width: 64,
  },
  friendOnlineDot: {
    position: 'absolute',
    bottom: 2,
    right: 2,
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: C.online,
    borderWidth: 2,
    borderColor: C.bg,
  },
  friendName: {
    color: C.text,
    fontSize: 12,
    textAlign: 'center',
    fontWeight: '500',
  },

  // Onglets Discussions / Groupes
  tabBar: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginBottom: 12,
    backgroundColor: C.card,
    borderRadius: 14,
    padding: 4,
    borderWidth: 1,
    borderColor: C.border,
  },
  tab: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 10,
    alignItems: 'center',
  },
  tabActive: {
    backgroundColor: C.cardHighlight,
  },
  tabText: {
    fontSize: 13,
    fontWeight: '500',
    color: C.textSecondary,
  },
  tabTextActive: {
    color: C.text,
    fontWeight: '700',
  },

  // Conversations de groupe
  convItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 14,
    backgroundColor: C.card,
    marginHorizontal: 20,
    marginBottom: 10,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: C.border,
  },
  groupBadge: {
    position: 'absolute',
    bottom: -2,
    right: -2,
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: C.accent,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: C.card,
  },
  groupBadgeText: {
    color: C.accentText,
    fontSize: 9,
    fontWeight: '800',
  },
  convBody: {
    flex: 1,
  },
  convTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  convName: {
    fontSize: 15,
    fontWeight: '700',
    color: C.text,
    flex: 1,
    marginRight: 8,
  },
  convTime: {
    fontSize: 12,
    color: C.textSecondary,
  },
  convBottom: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  convPreview: {
    fontSize: 13,
    color: C.textSecondary,
    flex: 1,
    marginRight: 8,
  },
  arrowPill: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: C.cardHighlight,
    alignItems: 'center',
    justifyContent: 'center',
  },
  arrowPillText: {
    color: C.accent,
    fontSize: 14,
    fontWeight: '700',
    marginTop: -2,
  },
  emptyGroupsCard: {
    marginHorizontal: 20,
    backgroundColor: C.card,
    borderRadius: 18,
    padding: 24,
    alignItems: 'center',
    marginTop: 20,
    borderWidth: 1,
    borderColor: C.border,
  },
  emptyGroupsTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: C.text,
    marginBottom: 6,
  },
  emptyGroupsSub: {
    fontSize: 13,
    color: C.textSecondary,
    textAlign: 'center',
    lineHeight: 18,
  },

  // Bouton d'action flottant (+)
  fab: {
    position: 'absolute',
    bottom: 30,
    right: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: C.accent,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
    elevation: 8,
    zIndex: 99,
  },
  fabText: {
    color: C.accentText,
    fontSize: 32,
    fontWeight: '300',
    marginTop: -2,
  },

  // ── Bottom Sheet Modal Animé ────────────────
  bottomSheetOverlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  bottomSheetBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.65)',
  },
  bottomSheetCard: {
    backgroundColor: C.card,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 24,
    paddingTop: 14,
    paddingBottom: BOTTOM_PADDING + 20,
    borderWidth: 1,
    borderColor: C.border,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.35,
    shadowRadius: 12,
    elevation: 12,
  },
  bottomSheetDragHandle: {
    width: 44,
    height: 4,
    borderRadius: 2,
    backgroundColor: C.muted,
    alignSelf: 'center',
    marginBottom: 18,
  },
  bottomSheetHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 20,
  },
  bottomSheetTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: C.text,
    marginBottom: 4,
  },
  bottomSheetSub: {
    fontSize: 13,
    color: C.textSecondary,
  },
  bottomSheetCloseBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: C.cardHighlight,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bottomSheetCloseText: {
    color: C.textSecondary,
    fontSize: 14,
    fontWeight: '700',
  },

  // ── Chat Screen ─────────────────────────────
  chatSafe: {
    flex: 1,
    backgroundColor: C.bg,
    paddingTop: STATUS_BAR_HEIGHT,
  },
  chatHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: C.card,
    borderBottomWidth: 1,
    borderBottomColor: C.border,
  },
  backBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: C.cardHighlight,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  backBtnText: {
    color: C.text,
    fontSize: 24,
    fontWeight: '400',
    marginTop: -2,
  },
  chatHeaderTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: C.text,
  },
  chatHeaderSub: {
    fontSize: 12,
    color: C.textSecondary,
    marginTop: 2,
  },
  infoBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 8,
    backgroundColor: C.cardHighlight,
  },
  infoBannerText: {
    color: C.accent,
    fontSize: 13,
  },
  errorBanner: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: 'rgba(239, 68, 68, 0.12)',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(239, 68, 68, 0.25)',
  },
  errorBannerText: {
    color: '#fca5a5',
    fontSize: 13,
  },
  retryBtn: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderRadius: 8,
  },
  retryText: {
    color: '#f87171',
    fontWeight: '600',
    fontSize: 13,
  },
  messageList: {
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  bubbleWrapper: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    marginBottom: 12,
    maxWidth: width * 0.78,
  },
  bubbleWrapperOwn: {
    alignSelf: 'flex-end',
    flexDirection: 'row-reverse',
  },
  bubbleWrapperOther: {
    alignSelf: 'flex-start',
  },
  bubble: {
    paddingHorizontal: 15,
    paddingVertical: 10,
    borderRadius: 18,
    maxWidth: '100%',
  },
  bubbleOwn: {
    backgroundColor: C.accent,
    borderBottomRightRadius: 4,
  },
  bubbleOther: {
    backgroundColor: C.card,
    borderBottomLeftRadius: 4,
    borderWidth: 1,
    borderColor: C.border,
  },
  bubbleSender: {
    fontSize: 12,
    fontWeight: '700',
    color: C.accent,
    marginBottom: 4,
  },
  bubbleText: {
    fontSize: 15,
    color: C.text,
    lineHeight: 21,
  },
  bubbleTextOwn: {
    color: C.accentText,
    fontWeight: '500',
  },
  bubbleTime: {
    fontSize: 10,
    color: C.textSecondary,
    marginTop: 4,
    alignSelf: 'flex-end',
  },
  bubbleTimeOwn: {
    color: 'rgba(15, 17, 21, 0.65)',
  },
  emptyChat: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 80,
  },
  emptyChatTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: C.text,
    marginBottom: 6,
  },
  emptyChatSub: {
    fontSize: 14,
    color: C.textSecondary,
    textAlign: 'center',
  },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
    paddingBottom: Platform.OS === 'ios' ? 28 : 18,
    backgroundColor: C.card,
    borderTopWidth: 1,
    borderTopColor: C.border,
  },
  chatInput: {
    flex: 1,
    backgroundColor: C.bg,
    borderRadius: 22,
    paddingHorizontal: 18,
    paddingVertical: 12,
    fontSize: 15,
    color: C.text,
    borderWidth: 1,
    borderColor: C.border,
    maxHeight: 120,
  },
  sendBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: C.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendBtnDisabled: {
    opacity: 0.4,
  },
  sendIcon: {
    color: C.accentText,
    fontSize: 22,
    fontWeight: '700',
    marginTop: -2,
  },

  // ── Profile Screen ──────────────────────────
  profileSafe: {
    flex: 1,
    backgroundColor: C.bg,
    paddingTop: STATUS_BAR_HEIGHT,
  },
  profileHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  profileHeaderTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: C.text,
  },
  profileAvatarSection: {
    alignItems: 'center',
    paddingVertical: 20,
  },
  profileName: {
    fontSize: 24,
    fontWeight: '800',
    color: C.text,
    marginBottom: 4,
  },
  profileHandle: {
    fontSize: 14,
    color: C.textSecondary,
  },
  greenDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: C.online,
  },
  onlineLabel: {
    fontSize: 13,
    color: C.textSecondary,
  },
  settingsCard: {
    backgroundColor: C.card,
    marginHorizontal: 20,
    borderRadius: 18,
    marginBottom: 16,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: C.border,
  },
  settingsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  settingsTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: C.text,
    marginBottom: 2,
  },
  settingsSub: {
    fontSize: 12,
    color: C.textSecondary,
  },
  settingsArrow: {
    fontSize: 22,
    color: C.textSecondary,
  },
  settingsDivider: {
    height: 1,
    backgroundColor: C.border,
    marginHorizontal: 20,
  },
  logoutBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 20,
    paddingVertical: 16,
    borderRadius: 16,
    backgroundColor: C.card,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.3)',
    marginBottom: 16,
  },
  logoutText: {
    fontSize: 15,
    color: C.danger,
    fontWeight: '600',
  },
});
