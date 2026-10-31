# CaixaX

App Android (Kotlin + Jetpack Compose) para controle mensal de despesas e de dinheiro guardado.

## Abrir e compilar

1. Abra a pasta `CaixaX` no Android Studio (Ladybug ou mais novo, JDK 17+).
2. Deixe o Gradle sincronizar (baixa AGP 8.7.3, Kotlin 2.1.0, Compose, Room).
3. Rode no aparelho, ou gere o APK com `./gradlew assembleRelease`; ele sai em `app/build/outputs/apk/release/`.
4. Testes da lógica de recorrência: `./gradlew test`.

O release está assinado com a chave **debug** (para instalar direto no seu celular). Antes de distribuir, crie uma keystore própria.

## Backup no Google Drive (passo único)

O app usa a pasta oculta do app no seu Drive (`drive.appdata`). Para o Google liberar o acesso:

1. No [Google Cloud Console](https://console.cloud.google.com/), crie ou escolha um projeto e ative a **Google Drive API**.
2. Em *APIs e serviços → Tela de consentimento OAuth*, configure como **Externo**, em modo de teste, e adicione seu e-mail em *Usuários de teste*.
3. Em *Credenciais → Criar credencial → ID do cliente OAuth*, escolha o tipo **Android**:
   - Nome do pacote: `br.com.brandelero.caixax`
   - SHA-1: rode `./gradlew signingReport` e copie o SHA-1 da variante que você instala (debug e release usam a mesma chave aqui).
4. Pronto. No app: **Ajustes → Conectar Google Drive**.

Depois de conectar, o backup roda uma vez por dia com internet (WorkManager) e também pelo botão **Backup agora**. **Restaurar** substitui todos os dados do aparelho pelo último backup e reinicia o app.

## Como funciona

- **Lançamentos**: receita ou despesa, com categoria, valor e dia de vencimento opcional. Podem ser *únicos* ou *recorrentes* (N meses ou sem prazo); parcelado é recorrente de N meses e aparece como "4/10".
- **Pago/pendente**: o mês mostra o *saldo previsto* (tudo) e o *realizado* (só o que já foi pago ou recebido). O que ficou pendente em meses anteriores aparece num card "em atraso".
- **Editar ou excluir uma recorrência**: só este mês, este e os próximos, ou todos. Mudar o número de meses vale sempre para a série inteira.
- **Guardado**: lugares (Nubank, Binance…) com aportes e retiradas em BRL, USD, EUR ou qualquer cripto da CoinGecko. O valor em reais é calculado pela cotação do dia, que fica em cache para uso offline. Cada movimentação pode ou não afetar o saldo do mês.
- **Privacidade**: bloqueio por digital ou PIN do aparelho (com 30 s de tolerância ao voltar ao app), ocultar valores e bloquear prints.

## Estrutura

```
app/src/main/java/br/com/brandelero/caixax/
  App.kt              Application + preferências
  domain/Rules.kt     regras de recorrência e parse de valores (puro, testado)
  data/Db.kt          entidades e DAO do Room
  data/Repo.kt        mês, edição por escopo, patrimônio
  data/CoinGecko.kt   cotações e busca de cripto
  data/Drive.kt       backup no Drive + worker diário
  ui/                 telas Compose (Mês, Guardado, Ajustes)
```
