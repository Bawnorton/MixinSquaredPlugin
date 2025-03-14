package com.bawnorton.msp.reference

import com.bawnorton.msp.reference.selector.TargetHandlerMixinReference
import com.bawnorton.msp.reference.selector.TargetHandlerNameReference
import com.bawnorton.msp.reference.selector.TargetHandlerPrefixReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar

class TargetHandlerReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            TargetHandlerMixinReference.ELEMENT_PATTERN,
            TargetHandlerMixinReference
        )

        registrar.registerReferenceProvider(
            TargetHandlerNameReference.ELEMENT_PATTERN,
            TargetHandlerNameReference
        )

        registrar.registerReferenceProvider(
            TargetHandlerPrefixReference.ELEMENT_PATTERN,
            TargetHandlerPrefixReference
        )
    }
}