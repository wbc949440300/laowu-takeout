# 客服改为半屏弹窗：不跳页、点✕收起、不占全屏
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = "D:\IDEA\sky-miniapp"

# ---------- 1. index.js：加聊天状态和方法 ----------
$f = "$root\pages\index\index.js"
$t = [IO.File]::ReadAllText($f, $utf8)

$dataFields = @'
			showChat: false,
			chatMessages: [{ role: 'bot', content: '您好，我是老吴外卖智能客服，请问有什么可以帮您？' }],
			chatInput: '',
			chatThreadId: '',
			chatLoading: false,
'@
if (-not $t.Contains('showChat: false')) {
	$t = [regex]::Replace($t, 'return \{', ("return {" + "`n" + $dataFields), 1)
}

$t = $t.Replace("goChat() { uni.navigateTo({ url: '/pages/chat/chat' }) },", "toggleChat() { this.showChat = !this.showChat },")

$chatMethods = @'
		sendChatMsg() {
				const text = (this.chatInput || '').trim()
				if (!text || this.chatLoading) return
				this.chatInput = ''
				this.chatMessages.push({ role: 'user', content: text })
				this.doChat({ message: text })
			},
			doChat(payload) {
				if (this.chatThreadId) payload.thread_id = this.chatThreadId
				this.chatLoading = true
				uni.request({
					url: 'http://127.0.0.1:8000/agent/chat',
					method: 'POST',
					data: payload,
					header: { 'Content-Type': 'application/json', 'authentication': uni.getStorageSync('sky_token') || '' },
					success: (res) => {
						this.chatLoading = false
						const data = res.data || {}
						if (data.thread_id) this.chatThreadId = data.thread_id
						if (data.type === 'confirm') {
							const info = data.interrupt || {}
							uni.showModal({
								title: '操作确认',
								content: '确认执行：' + (info.action || '') + ' ' + JSON.stringify(info.args || {}),
								success: (r) => {
									this.chatMessages.push({ role: 'user', content: r.confirm ? '确认执行' : '取消操作' })
									this.doChat({ resume: !!r.confirm })
								}
							})
						} else if (data.type === 'reply') {
							this.chatMessages.push({ role: 'bot', content: data.answer || '（无回复）' })
						} else {
							this.chatMessages.push({ role: 'bot', content: '出错了：' + (data.msg || JSON.stringify(data)) })
						}
					},
					fail: () => {
						this.chatLoading = false
						this.chatMessages.push({ role: 'bot', content: '客服服务连接失败，请确认 Agent 服务已启动（8000 端口）' })
					}
				})
			},
'@
if (-not $t.Contains('sendChatMsg')) {
	$t = $t.Replace("toggleChat() { this.showChat = !this.showChat },", ("toggleChat() { this.showChat = !this.showChat },`n" + $chatMethods))
}
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("1. index.js -> toggleChat=" + $t.Contains('toggleChat') + " sendChatMsg=" + $t.Contains('sendChatMsg'))

# ---------- 2. index.vue：弹窗模板 ----------
$f = "$root\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)
$t = $t.Replace('@click="goChat"', '@click="toggleChat"')

$popup = @'
<view class="cs_mask" v-if="showChat" @click="toggleChat"></view>
<view class="cs_panel" v-if="showChat">
	<view class="cs_head">
		<text class="cs_title">智能客服</text>
		<text class="cs_close" @click="toggleChat">✕</text>
	</view>
	<scroll-view class="cs_msgs" scroll-y>
		<view v-for="(msg, i) in chatMessages" :key="i" :class="['cs_row', msg.role === 'user' ? 'cs_right' : 'cs_left']">
			<view :class="['cs_bubble', msg.role === 'user' ? 'cs_user_b' : 'cs_bot_b']">{{msg.content}}</view>
		</view>
		<view v-if="chatLoading" class="cs_row cs_left"><view class="cs_bubble cs_bot_b">正在思考...</view></view>
	</scroll-view>
	<view class="cs_input_bar">
		<input class="cs_input" v-model="chatInput" placeholder="输入问题，回车发送" confirm-type="send" @confirm="sendChatMsg" />
		<view class="cs_send" @click="sendChatMsg">发送</view>
	</view>
</view>
</template>
'@
if (-not $t.Contains('cs_panel')) {
	$t = [regex]::Replace($t, '</template>', $popup, 1)
}

$style = @'
<style>
	.cs_mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.3); z-index: 998; }
	.cs_panel { position: fixed; left: 0; right: 0; bottom: 0; height: 70vh; background: #f5f6fa; border-radius: 24rpx 24rpx 0 0; display: flex; flex-direction: column; z-index: 999; }
	.cs_head { display: flex; align-items: center; justify-content: space-between; padding: 20rpx 30rpx; background: #fff; border-bottom: 1rpx solid #eee; }
	.cs_title { font-size: 32rpx; font-weight: bold; color: #333; }
	.cs_close { font-size: 36rpx; color: #999; padding: 0 10rpx; }
	.cs_msgs { flex: 1; padding: 20rpx; box-sizing: border-box; }
	.cs_row { display: flex; margin-bottom: 20rpx; }
	.cs_left { justify-content: flex-start; }
	.cs_right { justify-content: flex-end; }
	.cs_bubble { max-width: 75%; padding: 16rpx 22rpx; border-radius: 14rpx; font-size: 26rpx; line-height: 1.6; word-break: break-all; }
	.cs_bot_b { background: #fff; color: #333; border: 1rpx solid #e5e7eb; }
	.cs_user_b { background: #3b82f6; color: #fff; }
	.cs_input_bar { display: flex; align-items: center; padding: 16rpx 20rpx; background: #fff; border-top: 1rpx solid #eee; padding-bottom: calc(16rpx + env(safe-area-inset-bottom)); }
	.cs_input { flex: 1; background: #f2f3f5; border-radius: 10rpx; padding: 14rpx 18rpx; font-size: 26rpx; }
	.cs_send { margin-left: 14rpx; background: #ffc200; color: #333; padding: 14rpx 28rpx; border-radius: 10rpx; font-size: 26rpx; }
</style>
'@
if (-not $t.Contains('.cs_panel {')) { $t = $t + "`n" + $style }
[IO.File]::WriteAllText($f, $t, $utf8)
Write-Output ("2. index.vue -> cs_panel=" + $t.Contains('cs_panel') + " toggleChat=" + $t.Contains('@click="toggleChat"'))

# ---------- 3. 清理旧的全屏聊天页 ----------
if (Test-Path "$root\pages\chat") { Remove-Item "$root\pages\chat" -Recurse -Force; Write-Output "3. old chat page removed" }
$f = "$root\pages.json"
$t = [IO.File]::ReadAllText($f, $utf8)
if ($t.Contains('pages/chat/chat')) {
	$t = $t.Replace('{ "path" : "pages/chat/chat", "style" : { "navigationBarTitleText" : "智能客服" } },', '')
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output "4. pages.json entry removed"
} else { Write-Output "4. pages.json no entry" }
Write-Output "DONE"
