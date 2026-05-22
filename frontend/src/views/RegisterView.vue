<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const { register } = useAuth()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  username: '',
  password: '',
  passwordConfirm: '',
  nickname: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '长度 4-32', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '长度 6-64', trigger: 'blur' }
  ],
  passwordConfirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, cb) => {
        if (value !== form.password) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined
    })
  } catch {
    /* ElMessage handled */
  } finally {
    submitting.value = false
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <div class="brand">
          <div class="brand-logo">行</div>
          <div class="brand-name">行业研报多 Agent 协作平台</div>
        </div>
        <div class="auth-title">注册</div>
        <div class="auth-subtitle">创建一个新账号</div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4-32 位" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="form.nickname" placeholder="留空将使用用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-64 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="passwordConfirm">
          <el-input
            v-model="form.passwordConfirm"
            type="password"
            show-password
            placeholder="再输入一次"
          />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="auth-submit" @click="onSubmit">
          注册
        </el-button>
      </el-form>

      <div class="auth-footer">
        已有账号？<a @click.prevent="goLogin">去登录</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f7fb 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 380px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-popover);
  padding: 32px;
}

.auth-header {
  margin-bottom: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.brand-name {
  font-size: 14px;
  color: var(--text-secondary);
}

.auth-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}

.auth-subtitle {
  margin-top: 4px;
  color: var(--text-tertiary);
}

.auth-submit {
  width: 100%;
  margin-top: 8px;
}

.auth-footer {
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-secondary);
  text-align: center;
}

.auth-footer a {
  color: var(--color-primary);
  cursor: pointer;
}
</style>
