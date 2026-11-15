package com.forgequest.app.data.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Error returned by the Friends Worker or network transport. */
class FriendsException(val code: String, val http: Int) : Exception(code)

/** HTTP client for the ForgeQuest Friends Worker (`/v1/...`). */
class FriendsApi(
    http: OkHttpClient,
    private val baseUrl: () -> String,
    private val session: () -> String?,
) {
    private val client = http.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun authGoogle(idToken: String): AuthResponse =
        post("/v1/auth/google", buildJsonObject { put("idToken", idToken) }, AuthResponse.serializer(), auth = false)

    suspend fun me(): Me = get("/v1/me", Me.serializer())

    suspend fun putMe(name: String, sharePhoto: Boolean, sharePrs: Boolean): Me {
        val body = buildJsonObject {
            put("name", name)
            put("sharePhoto", sharePhoto)
            put("sharePrs", sharePrs)
        }
        return put("/v1/me", body, Me.serializer())
    }

    suspend fun rotateCode(): Me = post("/v1/me/code", null, Me.serializer())

    suspend fun putSnapshot(snapshot: Snapshot) {
        val payload = JsonObject(json.encodeToJsonElement(Snapshot.serializer(), snapshot).jsonObject - "updatedAt")
        call("PUT", "/v1/snapshot", jsonBody(payload.toString()), auth = true)
    }

    suspend fun friends(): FriendsView = get("/v1/friends", FriendsView.serializer())

    suspend fun invite(code: String): InviteResult =
        post("/v1/invites", buildJsonObject { put("code", code) }, InviteResult.serializer())

    suspend fun accept(fromId: String) {
        call("POST", "/v1/invites/${enc(fromId)}/accept", EMPTY_BODY, auth = true)
    }

    suspend fun decline(id: String) {
        call("POST", "/v1/invites/${enc(id)}/decline", EMPTY_BODY, auth = true)
    }

    suspend fun remove(id: String) {
        call("DELETE", "/v1/friends/${enc(id)}", null, auth = true)
    }

    suspend fun block(id: String) {
        call("POST", "/v1/blocks/${enc(id)}", EMPTY_BODY, auth = true)
    }

    suspend fun unblock(id: String) {
        call("DELETE", "/v1/blocks/${enc(id)}", null, auth = true)
    }

    suspend fun nudge(id: String): NudgeResult =
        post("/v1/nudges/${enc(id)}", null, NudgeResult.serializer())

    suspend fun putPush(token: String, platform: String = "android") {
        val body = buildJsonObject {
            put("token", token)
            put("platform", platform)
        }
        call("PUT", "/v1/push", jsonBody(body.toString()), auth = true)
    }

    suspend fun deletePush(token: String) {
        val body = buildJsonObject { put("token", JsonPrimitive(token)) }
        call("DELETE", "/v1/push", jsonBody(body.toString()), auth = true)
    }

    suspend fun deleteMe() {
        call("DELETE", "/v1/me", null, auth = true)
    }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>): T {
        val text = call("GET", path, null, auth = true)
        return json.decodeFromString(serializer, text)
    }

    private suspend fun <T> post(
        path: String,
        body: JsonObject?,
        serializer: KSerializer<T>,
        auth: Boolean = true,
    ): T {
        val reqBody = if (body != null) jsonBody(body.toString()) else EMPTY_BODY
        val text = call("POST", path, reqBody, auth)
        return json.decodeFromString(serializer, text)
    }

    private suspend fun <T> put(path: String, body: JsonObject, serializer: KSerializer<T>): T {
        val text = call("PUT", path, jsonBody(body.toString()), auth = true)
        return json.decodeFromString(serializer, text)
    }

    private suspend fun call(
        method: String,
        path: String,
        body: RequestBody?,
        auth: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val url = "${baseUrl().trimEnd('/')}$path"
        val builder = Request.Builder().url(url)
        if (auth) {
            val token = session() ?: throw FriendsException("unauthorized", 401)
            builder.header("Authorization", "Bearer $token")
        }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: EMPTY_BODY)
            "PUT" -> builder.put(body ?: EMPTY_BODY)
            "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
            else -> builder.method(method, body)
        }
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (_: IOException) {
            throw FriendsException("network", 0)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val code = parseErrorCode(text) ?: "http_${r.code}"
                throw FriendsException(code, r.code)
            }
            text
        }
    }

    private fun parseErrorCode(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun jsonBody(raw: String): RequestBody = raw.toRequestBody(JSON_MEDIA)

    private fun enc(segment: String): String = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    companion object {
        internal val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}
