package com.example.chatsamapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatbotApp()
        }
    }
}

class ChatViewModel : ViewModel() {
    var messages by mutableStateOf(listOf<Message>())
        private set
    var inputText by mutableStateOf("")
        private set

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    fun onInputChange(newInput: String) {
        inputText = newInput
    }

    fun sendMessage() {
        if (inputText.isNotBlank()) {
            viewModelScope.launch {
                messages = messages + Message(content = inputText, isUser = true)
                val aiResponse = getGroqResponse(inputText)
                messages = messages + Message(content = aiResponse, isUser = false)
                inputText = ""
            }
        }
    }

    private suspend fun getGroqResponse(prompt: String): String {
        return try {
            val apiKey = System.getenv("GROQ_API_KEY") ?: "your_api_key"
            val url = "https://api.groq.com/openai/v1/chat/completions"

            val requestBody = ChatRequest(
                model = "llama-3.1-70b-versatile",
                messages = listOf(ChatMessage("user", prompt))
            )

            val response: HttpResponse = client.post(url) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val chatCompletion = response.body<ChatCompletion>()
                chatCompletion.choices.firstOrNull()?.message?.content ?: "AI yanıtı alınamadı."
            } else {
                "API Hatası: ${response.status.description}"
            }
        } catch (e: Exception) {
            "Hata: ${e.localizedMessage}"
        }
    }
}

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletion(
    val id: String,
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String
)

@Serializable
data class Message(val content: String, val isUser: Boolean = false)

@Composable
fun ChatbotApp() {
    val viewModel: ChatViewModel = viewModel()

    // Dark theme support
    val darkMode = isSystemInDarkTheme()

    MaterialTheme(
        colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(),
        typography = Typography(
            bodyLarge = TextStyle(
                fontSize = 16.sp,
                color = if (darkMode) Color.White else Color.Black
            )
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp) // More compact padding
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp) // Compact item padding
                ) {
                    items(viewModel.messages) { message ->
                        ChatBubble(message)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        shape = RoundedCornerShape(60),
                        value = viewModel.inputText,
                        onValueChange = { viewModel.onInputChange(it) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp), // Compact TextField height
                        placeholder = { Text("Type a message...") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp) // Modern font size
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        shape = RoundedCornerShape(70),
                        onClick = { viewModel.sendMessage() },
                        modifier = Modifier.height(45.dp)
                    ) {
                        Text("Send", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = message.content,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp), // More compact padding
                textAlign = TextAlign.Start,
                fontSize = 14.sp, // Modern and compact font size
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}

@Composable
fun isSystemInDarkTheme(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
}