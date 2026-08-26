# toDoListNoAI

## Descrição e objetivo

App Android de lista de tarefas (to-do list) simples: cadastrar, listar, marcar como concluída e deletar tarefas. Serve como atividade prática de Android nativo com arquitetura em camadas (data → repository → viewmodel → ui), sem depender de nenhuma lib de terceiros para state management ou DI — só o que o próprio Jetpack já oferece.

Projeto feito para aprendizado de conceitos de arquitetura, integração com banco de dados e reforço de conceitos vistos no primeiro semestre.

## Tecnologias utilizadas

- **Kotlin** — linguagem do projeto inteiro.
- **Jetpack Compose** — UI declarativa (`ui/TarefasScreen.kt`, `ui/FormularioTarefaScreen.kt`).
- **Room** — persistência local em SQLite (`data/Tarefa.kt`, `data/TarefaDao.kt`, `data/TarefaDatabase.kt`).
- **Coroutines / Flow** — leitura assíncrona e reativa do banco (`Flow<List<Tarefa>>` no DAO, `StateFlow` no ViewModel).
- **ViewModel** — retém estado da UI sobrevivendo a recomposições/rotação de tela.
- **Navigation Compose** — troca de telas dentro de um único `NavHost` (`navigation/AppNavigation.kt`).

## `TarefaRepository`

Fica em `Repository/TarefaRepository.kt`. Responsabilidade única: ser a fronteira entre o `TarefaDao` (que fala Room) e o resto do app. Ele não decide nada, só expõe as operações do DAO com nomes de domínio:

```kotlin
class TarefaRepository(private val dao: TarefaDao) {
    val tarefas: Flow<List<Tarefa>> = dao.listAll()

    suspend fun criar(tarefa: Tarefa) = dao.createTarefa(tarefa)
    suspend fun atualizar(tarefa: Tarefa) = dao.updateTarefa(tarefa)
    suspend fun deletar(tarefa: Tarefa) = dao.deleteTarefa(tarefa)
}
```

Isso existe pra ninguém acima dele (ViewModel, UI) precisar conhecer o `TarefaDao`/Room diretamente. Se um dia o app trocar Room por outra fonte (API, cache, o que for), só esse arquivo muda — `TarefaViewModel` nem percebe.

## `TarefaViewModel`

Arquivo: `viewmodel/TarefaViewModel.kt`.

Ele tem duas responsabilidades:
1. Guardar a lista de tarefas de um jeito que sobrevive a recomposições da tela.
2. Receber ações da UI (inserir, atualizar, deletar) e repassar pro repositório.

### A lista de tarefas e o `.stateIn(...)`

```kotlin
val tarefas: StateFlow<List<Tarefa>> = repository.tarefas
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
```

Vamos por partes, porque tem bastante coisa junta aqui.

`repository.tarefas` é um `Flow` — um fluxo de dados que emite uma nova lista toda vez que o banco muda. O problema desse `Flow` "cru" é que ele só existe enquanto alguém está observando ele, e cada observador dispara sua própria consulta no banco.

`.stateIn(...)` resolve isso transformando esse `Flow` em um `StateFlow`, que:
- sempre tem um valor guardado, mesmo sem ninguém observando naquele instante;
- é compartilhado — várias telas observando ao mesmo tempo não disparam várias consultas repetidas.

Os três parâmetros que ele pede:
- `scope = viewModelScope` — diz "rode essa coleta de dados enquanto esse ViewModel existir". Quando a tela é fechada de vez, para.
- `started = SharingStarted.WhileSubscribed(5_000)` — diz quando começar e parar de escutar o banco. Aqui, ele escuta enquanto tiver alguém olhando pra tela, e continua escutando por mais 5 segundos depois que a última pessoa "sai", só por segurança (por exemplo, se a tela girar rapidinho, não precisa reiniciar a consulta do zero).
- `initialValue = emptyList()` — o valor que a tela vê antes do banco responder pela primeira vez (a consulta é assíncrona, então não pode começar com algo vazio/nulo travando a tela).

### As ações (`inserir`, `atualizar`, `deletar`)

```kotlin
fun inserir(tarefa: Tarefa) = viewModelScope.launch { repository.criar(tarefa) }
```

Cada uma dessas funções abre uma coroutine (`viewModelScope.launch`) pra chamar o repositório, porque salvar no banco é uma operação `suspend` — não pode rodar direto na thread da UI.

### O `companion object`

```kotlin
companion object {
    fun factory(context: Context): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dao = TarefaDatabase.getDatabase(context).tarefaDao()
                return TarefaViewModel(TarefaRepository(dao)) as T
            }
        }
}
```

Esse pedaço existe por causa de um detalhe chato do Android: normalmente, quando você pede um ViewModel pro sistema, ele só sabe criar um usando construtor vazio (`TarefaViewModel()`). Só que o nosso `TarefaViewModel` exige um `TarefaRepository` no construtor. Sem isso, o Android não sabe como montar o objeto sozinho.

A solução é dar pro Android uma "receita" de como montar tudo — isso é a `Factory`. Ela monta a cadeia inteira, passo a passo:

1. Pega o banco (`TarefaDatabase.getDatabase(context)`).
2. Pega o DAO desse banco.
3. Cria o `TarefaRepository` com esse DAO.
4. Cria o `TarefaViewModel` com esse repositório.

O `companion object` só serve pra deixar essa receita acessível sem precisar de uma instância do `TarefaViewModel` já pronta — você chama `TarefaViewModel.factory(context)` direto, como se fosse uma função estática.

## Como a `ListaTarefasScreen` observa o estado e dispara ações

Função real: `ListTarefasScreen`, em `ui/TarefasScreen.kt`.

Primeira linha da tela:

```kotlin
val tarefas by viewModel.tarefas.collectAsStateWithLifecycle()
```

Isso conecta a tela ao `StateFlow` que vimos lá em cima. Toda vez que a lista de tarefas muda no banco, essa variável `tarefas` muda sozinha, e o Compose redesenha a tela automaticamente — não precisa escrever nenhum código pra "atualizar a tela na mão".

Quando o usuário marca uma tarefa como concluída ou deleta, a tela **não** mexe na lista diretamente. Ela só avisa o ViewModel:

```kotlin
onCheckedChange = { tarefa, concluido ->
    viewModel.atualizar(tarefa.copy(concluido = concluido))
},
onDeletar = { tarefa -> viewModel.deletar(tarefa) }
```

O caminho completo de uma ação é sempre esse: toque na tela → chama o ViewModel → ViewModel chama o repositório → repositório chama o DAO → Room salva no banco → o banco avisa o `Flow` que algo mudou → o `StateFlow` atualiza → a tela redesenha sozinha com a lista nova.

## Como a `FormularioTarefaScreen` diferencia cadastro e edição

É a mesma tela pros dois casos — o que muda é o `tarefaId` que ela recebe.

```kotlin
val tarefaExistente = remember(tarefas, tarefaId) {
    tarefas.find { it.id == tarefaId }
}
```

- Se `tarefaId` for `0`, não existe tarefa nenhuma com esse id (o Room começa a contar os ids a partir de 1). Então a tela entende isso como **"tarefa nova"**: os campos começam vazios, e ao salvar ela cria uma tarefa nova com `viewModel.inserir(...)`.
- Se `tarefaId` for diferente de `0`, a tela procura essa tarefa na lista atual. Se achar, entende como **"editar tarefa existente"**: os campos já vêm preenchidos com o título/descrição atuais, e ao salvar ela atualiza a tarefa (mantendo o mesmo `id`) com `viewModel.atualizar(...)`.

Tem também uma variável `isEdicao`, mas ela só serve pra mudar o texto do título da tela ("Nova Tarefa" ou "Editar Tarefa") — não muda a lógica de salvar.

## Rotas em `AppNavigation` e a passagem do ID da tarefa

```kotlin
NavHost(navController = navController, startDestination = "lista") {
    composable("lista") { ... }
    composable("formulario/{tarefaId}") { backStackEntry -> ... }
}
```

O app tem só duas telas:

- `"lista"` — é a tela inicial, mostra a lista de tarefas.
- `"formulario/{tarefaId}"` — é a tela de cadastro/edição. O `{tarefaId}` ali é um espaço reservado: quando alguém navega pra essa rota, o valor real do id entra no lugar dele (por exemplo, `"formulario/3"`).

Como o id chega até a tela de formulário, em duas etapas:

**1. Quem navega monta a URL com o id:**

```kotlin
onNovaTarefa = { navController.navigate("formulario/0") }
onEditarTarefa = { id -> navController.navigate("formulario/$id") }
```

Pra tarefa nova, usa `0` (nenhuma tarefa real tem esse id). Pra editar, usa o id de verdade da tarefa clicada.

**2. Quem recebe a rota lê esse id de volta:**

```kotlin
val tarefaId = backStackEntry.arguments?.getString("tarefaId")?.toInt() ?: 0
```

O `backStackEntry` traz os dados da navegação que aconteceu. `arguments?.getString("tarefaId")` pega o pedaço `{tarefaId}` da URL como texto, `.toInt()` converte pra número, e o `?: 0` é uma proteção: se por algum motivo vier nulo, assume `0` (modo "tarefa nova") em vez de quebrar o app.

Esse `tarefaId` é exatamente o que a `FormularioTarefaScreen` usa pra decidir entre cadastro e edição, como explicado na seção anterior.

## Como a `MainActivity` cria a ViewModel e inicia a navegação

```kotlin
setContent {
    ToDoListNoAITheme {
        val viewModel: TarefaViewModel = viewModel(
            factory = TarefaViewModel.factory(applicationContext)
        )
        AppNavigation(viewModel = viewModel)
    }
}
```

Passo a passo do que acontece quando o app abre:

1. `viewModel(factory = TarefaViewModel.factory(applicationContext))` pede ao Android um `TarefaViewModel`, usando a "receita" (a `factory`) que vimos lá em cima — é aqui que ela é usada de fato.
2. Esse `viewModel` é criado só uma vez. Mesmo que a tela recomponha várias vezes, o Android reaproveita a mesma instância — é assim que o estado (a lista de tarefas) não se perde à toa.
3. `AppNavigation(viewModel = viewModel)` entrega esse mesmo ViewModel pro sistema de navegação, que passa ele adiante pras duas telas (`ListTarefasScreen` e `FormularioTarefaScreen`).

Como as duas telas recebem o **mesmo** ViewModel, elas sempre veem a mesma lista de tarefas — não existe uma cópia da lista em cada tela.

## Como rodar

Pré-requisitos: Android Studio (ou JDK 17+ e o Android SDK configurados na mão) e um emulador ou celular com Android 7.0 (API 24) ou mais novo.

**Pelo Android Studio:**
1. Abrir a pasta do projeto.
2. Esperar o Gradle sincronizar sozinho.
3. Clicar em ▶ com um emulador/device selecionado.

**Pelo terminal:**
```bash
./gradlew assembleDebug      # gera o APK de debug
./gradlew installDebug       # instala num device/emulador já conectado
./gradlew :app:compileDebugKotlin   # só compila, pra checar erro rápido sem gerar APK
```

## Evidências

