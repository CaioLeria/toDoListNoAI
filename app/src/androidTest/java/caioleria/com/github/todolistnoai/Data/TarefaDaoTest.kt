package caioleria.com.github.todolistnoai.Data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import caioleria.com.github.todolistnoai.data.Tarefa
import caioleria.com.github.todolistnoai.data.TarefaDao
import caioleria.com.github.todolistnoai.data.TarefaDatabase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TarefaDaoTest {
    private lateinit var database: TarefaDatabase
    private lateinit var dao: TarefaDao

    @Before
    fun criarBanco() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TarefaDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.tarefaDao()
    }

    @After
    fun fecharBanco() {
        database.close()
    }

    @Test
    fun inserirTarefaEListar() = runTest {
        val tarefa = Tarefa(
            titulo = "Estudar Room",
            descricao = "Aprender Entity e DAO",
            dataCriacao = System.currentTimeMillis()
        )
        dao.createTarefa(tarefa)

        val tarefas = dao.listAll().first()
        assertEquals(1, tarefas.size)
        assertEquals("Estudar Room", tarefas[0].titulo)
        assertFalse(tarefas[0].concluido)
    }

    @Test
    fun marcarTarefaComoConcluida() = runTest {
        dao.createTarefa(
            Tarefa(titulo = "Tarefa 1", descricao = "", dataCriacao = System.currentTimeMillis())
        )
        val inserida = dao.listAll().first().first()

        dao.updateTarefa(inserida.copy(concluido = true))

        val atualizada = dao.listAll().first().first()
        assertTrue(atualizada.concluido)
    }

    @Test
    fun deletarTarefa() = runTest {
        dao.createTarefa(
            Tarefa(titulo = "Para deletar", descricao = "", dataCriacao = System.currentTimeMillis())
        )
        val inserida = dao.listAll().first().first()

        dao.deleteTarefa(inserida)

        val tarefas = dao.listAll().first()
        assertTrue(tarefas.isEmpty())
    }
}
