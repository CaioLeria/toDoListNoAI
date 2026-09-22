package caioleria.com.github.todolistnoai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TarefaDao {
    @Query("""
        SELECT * FROM tarefas
        ORDER BY dataHora IS NULL, dataHora ASC, dataCriacao DESC
    """)
    fun listAll(): Flow<List<Tarefa>>

@Insert
suspend fun createTarefa(tarefa: Tarefa)

@Update
suspend fun updateTarefa (tarefa: Tarefa)

@Delete
suspend fun deleteTarefa(tarefa: Tarefa)
}