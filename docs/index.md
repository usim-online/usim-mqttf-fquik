---
layout: page
---
<script setup>
import { onMounted } from 'vue'

onMounted(() => {
  const lang = navigator.language.startsWith('zh') ? '/zh/' : '/en/'
  location.href = lang
})
</script>
