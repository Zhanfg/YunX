package com.yunx.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.provider.CloudProviderDescriptor
import com.yunx.app.data.provider.CloudProviderRegistry
import com.yunx.app.data.provider.ProviderReadiness
import com.yunx.app.data.provider.PublicShareResolvers

private val visiblePublicProviders = listOf(
    "google_drive",
    "onedrive",
    "icloud_drive",
    "dropbox",
    "pcloud"
)

fun isRecognizedShareLink(text: String): Boolean =
    ShareLinkParser.parse(text) != null || CloudProviderRegistry.detect(text) != null

fun detectShareProviderName(text: String): String? {
    ShareLinkParser.parse(text)?.let { parsed ->
        return when (parsed.platform.name) {
            "QUARK" -> "夸克网盘"
            "UC" -> "UC 网盘"
            "XUNLEI" -> "迅雷网盘"
            "BAIDU" -> "百度网盘"
            "C139" -> "139 网盘"
            "PAN123" -> "123 云盘"
            else -> parsed.platform.name
        }
    }
    return CloudProviderRegistry.detect(text)?.displayName
}

@Composable
fun PublicProviderOverview(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = "公开分享 · 无需登录",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "支持识别并下载公开文件；不会要求绑定私人账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            visiblePublicProviders.mapNotNull(CloudProviderRegistry::byId).forEach { provider ->
                PublicProviderRow(provider)
            }
        }
    }
}

@Composable
private fun PublicProviderRow(provider: CloudProviderDescriptor) {
    val implemented = provider.readiness == ProviderReadiness.PUBLIC_DOWNLOAD &&
        PublicShareResolvers.supports(provider.id)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(
                    text = providerBadge(provider.id),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(provider.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (implemented) "公开链接可直接解析" else "已识别，适配中",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (implemented) "可用" else "检测",
            style = MaterialTheme.typography.labelMedium,
            color = if (implemented) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun DetectedPublicProviderCard(link: String, modifier: Modifier = Modifier) {
    if (ShareLinkParser.parse(link) != null) return
    val provider = CloudProviderRegistry.detect(link) ?: return
    val supported = provider.readiness == ProviderReadiness.PUBLIC_DOWNLOAD &&
        PublicShareResolvers.supports(provider.id)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (supported) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = if (supported) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = "已识别：${provider.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (supported) {
                        "公开分享 · 无需登录 · 点击“开始解析”即可进入下载"
                    } else {
                        "链接已识别，但该 Provider 的公开取链仍在适配"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun providerBadge(id: String): String = when (id) {
    "google_drive" -> "G"
    "onedrive" -> "1D"
    "icloud_drive" -> "iC"
    "dropbox" -> "Db"
    "pcloud" -> "pC"
    else -> id.take(2)
}
