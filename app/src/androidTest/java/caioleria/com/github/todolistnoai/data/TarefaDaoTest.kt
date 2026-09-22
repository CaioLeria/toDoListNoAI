package caioleria.com.github.todolistnoai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val tarefa = Tarefa(titulo = "Estudar Room", descricao = "Aprender Entity e DAO")
        dao.createTarefa(tarefa)

        val tarefas = dao.listAll().first()
        assertEquals(1, tarefas.size)
        assertEquals("Estudar Room", tarefas[0].titulo)
        assertFalse(tarefas[0].concluido)
    }

    @Test
    fun marcarTarefaComoConcluida() = runTest {
        dao.createTarefa(Tarefa(titulo = "Tarefa 1", descricao = ""))
        val inserida = dao.listAll().first().first()

        dao.updateTarefa(inserida.copy(concluido = true))

        val atualizada = dao.listAll().first().first()
        assertTrue(atualizada.concluido)
    }

    @Test
    fun deletarTarefa() = runTest {
        dao.createTarefa(Tarefa(titulo = "Para deletar", descricao = ""))
        val inserida = dao.listAll().first().first()

        dao.deleteTarefa(inserida)

        val tarefas = dao.listAll().first()
        assertTrue(tarefas.isEmpty())
    }

    @Test
    fun tarefasComPrazoAparecemAntesDeAvulsasEOrdenadasPorProximidade() = runTest {
        val agora = System.currentTimeMillis()
        dao.createTarefa(Tarefa(titulo = "Avulsa", descricao = ""))
        dao.createTarefa(Tarefa(titulo = "Prazo distante", descricao = "", dataHora = agora + 100_000))
        dao.createTarefa(Tarefa(titulo = "Prazo proximo", descricao = "", dataHora = agora + 10_000))

        val tarefas = dao.listAll().first()

        assertEquals("Prazo proximo", tarefas[0].titulo)
        assertEquals("Prazo distante", tarefas[1].titulo)
        assertEquals("Avulsa", tarefas[2].titulo)
    }
}