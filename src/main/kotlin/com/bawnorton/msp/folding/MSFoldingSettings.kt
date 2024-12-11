package com.bawnorton.msp.folding

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "MixinSquaredFoldingSettings", storages = [Storage("mixin_squared.xml")])
class MSFoldingSettings : PersistentStateComponent<MSFoldingSettings.State> {

    data class State(
        var foldTargetHandlerMixin: Boolean = true
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        val instance: MSFoldingSettings
            get() = ApplicationManager.getApplication().getService(MSFoldingSettings::class.java)
    }
}