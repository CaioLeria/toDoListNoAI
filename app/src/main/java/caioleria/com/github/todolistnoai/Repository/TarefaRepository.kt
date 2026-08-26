package caioleria.com.github.todolistnoai.Repository

import caioleria.com.github.todolistnoai.data.Tarefa
import caioleria.com.github.todolistnoai.data.TarefaDao
import kotlinx.coroutines.flow.Flow

class TarefaRepository(private val dao: TarefaDao) {
    val tarefas: Flow<List<Tarefa>> = dao.listAll()

    suspend fun criar(tarefa: Tarefa) = dao.createTarefa(tarefa)
    suspend fun atualizar(tarefa: Tarefa) = dao.updateTarefa(tarefa)
    suspend fun deletar(tarefa: Tarefa) = dao.deleteTarefa(tarefa)
}
