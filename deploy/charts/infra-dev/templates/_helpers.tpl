{{- define "infra-dev.labels" -}}
app.kubernetes.io/part-of: platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
hashmatrix.io/tier: dev-infra
{{- end -}}

{{- define "infra-dev.keycloak.image" -}}
{{- $reg := .Values.global.imageRegistry | default "" -}}
{{- $img := printf "%s:%s" .Values.keycloak.image.repository .Values.keycloak.image.tag -}}
{{- if $reg }}{{ printf "%s/%s" $reg $img }}{{- else }}{{ $img }}{{- end }}
{{- end -}}

{{- define "infra-dev.echoStub.image" -}}
{{- $reg := .Values.global.imageRegistry | default "" -}}
{{- $img := printf "%s:%s" .Values.echoStub.image.repository .Values.echoStub.image.tag -}}
{{- if $reg }}{{ printf "%s/%s" $reg $img }}{{- else }}{{ $img }}{{- end }}
{{- end -}}
