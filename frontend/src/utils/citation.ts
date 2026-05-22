import type MarkdownIt from 'markdown-it'

/**
 * markdown-it 插件：把行内文本里的 `[1]` `[2]`… 替换成
 *   <sup class="cite-ref" data-cite="1">[1]</sup>
 * 这样模板可以用 click delegation 监听 `.cite-ref` 实现 citation 联动。
 *
 * 只匹配方括号中**纯数字**的引用，避免误伤 markdown 链接 [text](url)。
 */
export function citationPlugin(md: MarkdownIt): void {
  const CITE_RE = /\[(\d+)\]/g

  md.core.ruler.push('citation_inline', (state) => {
    for (const token of state.tokens) {
      if (token.type !== 'inline' || !token.children) continue

      const newChildren: typeof token.children = []

      for (const child of token.children) {
        if (child.type !== 'text' || !CITE_RE.test(child.content)) {
          newChildren.push(child)
          continue
        }
        CITE_RE.lastIndex = 0

        // 拆分 text 并交叉插入 html_inline token
        const text = child.content
        let lastIdx = 0
        let m: RegExpExecArray | null
        const re = new RegExp(CITE_RE.source, 'g')
        while ((m = re.exec(text)) != null) {
          if (m.index > lastIdx) {
            const t = new state.Token('text', '', 0)
            t.content = text.slice(lastIdx, m.index)
            newChildren.push(t)
          }
          const t = new state.Token('html_inline', '', 0)
          t.content = `<sup class="cite-ref" data-cite="${m[1]}">[${m[1]}]</sup>`
          newChildren.push(t)
          lastIdx = m.index + m[0].length
        }
        if (lastIdx < text.length) {
          const t = new state.Token('text', '', 0)
          t.content = text.slice(lastIdx)
          newChildren.push(t)
        }
      }

      token.children = newChildren
    }
  })
}
