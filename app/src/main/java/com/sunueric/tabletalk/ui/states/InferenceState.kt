package com.sunueric.tabletalk.ui.states

sealed interface InferenceState {
    data object Idle : InferenceState
    data class Thinking(val currentStep: String) : InferenceState
    data class Success(val answer: String) : InferenceState
    data class Error(val message: String) : InferenceState
}