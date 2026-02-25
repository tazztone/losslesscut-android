package com.tazztone.losslesscut

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.classes
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureTest {

    @Test
    fun `domain module has no Android imports`() {
        Konsist.scopeFromModule(":core:domain")
            .files
            .assertTrue { file ->
                file.imports.none {
                    it.name.startsWith("android.") || it.name.startsWith("androidx.")
                }
            }
    }

    @Test
    fun `domain module has no Hilt imports`() {
        Konsist.scopeFromModule(":core:domain")
            .files
            .assertTrue { file ->
                file.imports.none {
                    it.name.startsWith("dagger.hilt.")
                }
            }
    }

    @Test
    fun `engine module does not import data layer utilities`() {
        Konsist.scopeFromModule(":engine")
            .files
            .assertTrue { file ->
                file.imports.none {
                    it.name.startsWith("com.tazztone.losslesscut.utils.StorageUtils") ||
                    it.name.startsWith("com.tazztone.losslesscut.data.")
                }
            }
    }

    @Test
    fun `use cases reside in domain usecase package`() {
        Konsist.scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("..domain.usecase..") }
    }

    @Test
    fun `use cases follow naming convention`() {
        Konsist.scopeFromProduction()
            .classes()
            .withPackage("..domain.usecase..")
            .filter { it.isTopLevel }
            .assertTrue { it.name.endsWith("UseCase") }
    }
}
