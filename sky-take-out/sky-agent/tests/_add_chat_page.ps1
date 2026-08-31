# 小程序新增智能客服聊天页 + 首页入口按钮
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = "D:\IDEA\sky-miniapp"

# ---------- 1. 新建聊天页 pages/chat/chat.vue ----------
$chatVue = @'
<template>
	<view class="chat_page">
		<scroll-view class="msg_list" scroll-y :scroll-top="scrollTop" :scroll-with-animation="true">
			<view v-for="(msg, i) in messages" :key="i" :class="['msg_row', msg.role === 'user' ? 'row_right' : 'row_left']">
				<view :class="['bubble', msg.role === 'user' ? 'user_bubble' : 'bot_bubble']">{{msg.content}}</view>
			</view>
			<view v-if="loading" class="msg_row row_left">
				<view class="bubble bot_bubble">正在思考...</view>
			</view>
		</scroll-view>
		<view class="input_bar">
			<input class="msg_input" v-model="inputText" placeholder="输入问题，回车发送" confirm-type="send" @confirm="send" />
			<view class="send_btn" @click="send">发送</view>
		</view>
	</view>
</template>

<script>
	const AGENT_URL = 'http://127.0.0.1:8000/agent/chat'
	export default {
		data() {
			return {
				messages: [{ role: 'bot', content: '您好，我是老吴外卖智能客服，可以帮您查订单、催单、退款、推荐菜品，请问有什么可以帮您？' }],
				inputText: '',
				threadId: '',
				loading: false,
				scrollTop: 0
			}
		},
		methods: {
			scrollToBottom() {
				this.$nextTick(() => {
					this.scrollTop = this.scrollTop === 99999 ? 100000 : 99999
				})
			},
			send() {
				const text = (this.inputText || '').trim()
				if (!text || this.loading) return
				this.inputText = ''
				this.messages.push({ role: 'user', content: text })
				this.scrollToBottom()
				this.doChat({ message: text })
			},
			doChat(payload) {
				if (this.threadId) payload.thread_id = this.threadId
				this.loading = true
				uni.request({
					url: AGENT_URL,
					method: 'POST',
					data: payload,
					header: {
						'Content-Type': 'application/json',
						// 令牌自动取登录时保存的 sky_token，用户无感
						'authentication': uni.getStorageSync('sky_token') || ''
					},
					success: (res) => {
						this.loading = false
						const data = res.data || {}
						if (data.thread_id) this.threadId = data.thread_id
						if (data.type === 'confirm') {
							// 高危操作（如取消订单）弹窗确认
							const info = data.interrupt || {}
							uni.showModal({
								title: '操作确认',
								content: '确认执行：' + (info.action || '') + ' ' + JSON.stringify(info.args || {}),
								confirmText: '确认执行',
								cancelText: '取消',
								success: (r) => {
									this.messages.push({ role: 'user', content: r.confirm ? '确认执行' : '取消操作' })
									this.scrollToBottom()
									this.doChat({ resume: !!r.confirm })
								}
							})
						} else if (data.type === 'reply') {
							this.messages.push({ role: 'bot', content: data.answer || '（无回复）' })
							this.scrollToBottom()
						} else {
							this.messages.push({ role: 'bot', content: '出错了：' + (data.msg || JSON.stringify(data)) })
							this.scrollToBottom()
						}
					},
					fail: () => {
						this.loading = false
						this.messages.push({ role: 'bot', content: '客服服务连接失败，请确认 Agent 服务已启动（8000 端口）' })
						this.scrollToBottom()
					}
				})
			}
		}
	}
</script>

<style>
	.chat_page { display: flex; flex-direction: column; height: 100vh; background: #f5f6fa; }
	.msg_list { flex: 1; padding: 20rpx; box-sizing: border-box; }
	.msg_row { display: flex; margin-bottom: 24rpx; }
	.row_left { justify-content: flex-start; }
	.row_right { justify-content: flex-end; }
	.bubble { max-width: 70%; padding: 18rpx 24rpx; border-radius: 16rpx; font-size: 28rpx; line-height: 1.6; word-break: break-all; }
	.bot_bubble { background: #ffffff; color: #333; border: 1rpx solid #e5e7eb; border-top-left-radius: 4rpx; }
	.user_bubble { background: #3b82f6; color: #fff; border-top-right-radius: 4rpx; }
	.input_bar { display: flex; align-items: center; padding: 16rpx 20rpx; background: #fff; border-top: 1rpx solid #e5e7eb; padding-bottom: calc(16rpx + env(safe-area-inset-bottom)); }
	.msg_input { flex: 1; background: #f2f3f5; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; }
	.send_btn { margin-left: 16rpx; background: #ffc200; color: #333; padding: 16rpx 32rpx; border-radius: 12rpx; font-size: 28rpx; }
</style>
'@
$chatDir = "$root\pages\chat"
if (!(Test-Path $chatDir)) { New-Item -ItemType Directory -Path $chatDir | Out-Null }
[IO.File]::WriteAllText("$chatDir\chat.vue", $chatVue, $utf8)
Write-Output "1. chat.vue created"

# ---------- 2. pages.json 注册聊天页 ----------
$f = "$root\pages.json"
$t = [IO.File]::ReadAllText($f, $utf8)
$entry = '{ "path" : "pages/chat/chat", "style" : { "navigationBarTitleText" : "智能客服" } },'
if ($t.Contains('pages/chat/chat')) {
	Write-Output "2. pages.json already registered"
} else {
	$t2 = [regex]::Replace($t, '("pages"\s*:\s*\[)', ('$1' + $entry), 1)
	if ($t2 -eq $t) { Write-Output "2. pages.json PATTERN NOT MATCHED!" }
	else { [IO.File]::WriteAllText($f, $t2, $utf8); Write-Output "2. pages.json registered" }
}

# ---------- 3. 首页加入口按钮 ----------
$f = "$root\pages\index\index.vue"
$t = [IO.File]::ReadAllText($f, $utf8)
if ($t.Contains('cs_entry')) {
	Write-Output "3. index.vue already has entry"
} else {
	# 模板末尾插入悬浮按钮
	$btn = '<view class="cs_entry" @click="goChat">客服</view>' + "`n</template>"
	$t2 = [regex]::Replace($t, '</template>', $btn, 1)
	if ($t2 -eq $t) { Write-Output "3a. template tag NOT FOUND!" } else { $t = $t2 }
	# methods 里加 goChat
	$t2 = [regex]::Replace($t, 'methods:\s*\{', "methods: {`n`t`tgoChat() { uni.navigateTo({ url: '/pages/chat/chat' }) },", 1)
	if ($t2 -eq $t) { Write-Output "3b. methods NOT FOUND!" } else { $t = $t2 }
	# 样式
	$t = $t + "`n<style>`n`t.cs_entry { position: fixed; right: 30rpx; bottom: 200rpx; width: 100rpx; height: 100rpx; border-radius: 50%; background: #ffc200; color: #333; display: flex; align-items: center; justify-content: center; font-size: 28rpx; box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.2); z-index: 999; }`n</style>"
	[IO.File]::WriteAllText($f, $t, $utf8)
	Write-Output "3. index.vue entry added"
}
Write-Output "DONE"
