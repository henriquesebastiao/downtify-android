package com.henriquesebastiao.downtify.feature.musicai

import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailure
import com.henriquesebastiao.downtify.core.data.musicai.MusicAiFailureKind

fun musicAiFailureMessage(error: MusicAiFailure): String = when (error.kind) {
    MusicAiFailureKind.Timeout -> "Le serveur met trop de temps à répondre. Votre musique locale reste disponible."

    MusicAiFailureKind.Network -> "La connexion au serveur a échoué. Les données déjà chargées restent disponibles."

    MusicAiFailureKind.Authentication -> "Votre connexion au serveur doit être renouvelée."

    MusicAiFailureKind.Forbidden -> "Ce compte n’est pas autorisé à effectuer cette action."

    MusicAiFailureKind.Disabled -> "Cette fonction est désactivée. Retrouvez ses options dans Paramètres → IA."

    MusicAiFailureKind.Busy -> "Le moteur est occupé. Votre lecture actuelle continue."

    MusicAiFailureKind.NotReady -> "Le moteur prépare encore cette fonction. Votre lecture actuelle reste disponible."

    MusicAiFailureKind.Unsupported -> "Cette version du serveur ne propose pas cette fonction."

    MusicAiFailureKind.InvalidResponse ->
        "Le serveur a répondu, mais sa réponse est incompatible. " +
            "Vérifiez sa version et l’adresse configurée."

    MusicAiFailureKind.Other -> "Cette demande n’a pas abouti. Votre lecture actuelle reste disponible."
}
