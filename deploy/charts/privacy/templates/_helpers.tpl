{{- define "privacy.labels" -}}
app.kubernetes.io/part-of: privacy
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "privacy.enginePy.image" -}}
{{ .Values.enginePy.image.repository }}:{{ .Values.enginePy.image.tag | default .Chart.AppVersion }}
{{- end -}}

{{- define "privacy.orchestratorJava.image" -}}
{{ .Values.orchestratorJava.image.repository }}:{{ .Values.orchestratorJava.image.tag | default .Chart.AppVersion }}
{{- end -}}
