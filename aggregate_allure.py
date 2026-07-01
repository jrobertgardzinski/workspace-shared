import os
import json
import glob
from collections import defaultdict
from datetime import datetime

def format_duration(seconds):
    if seconds < 0.001:
        return "< 1ms"
    if seconds < 1:
        return f"{seconds*1000:.0f}ms"
    return f"{seconds:.2f}s"

def get_label(data, label_name):
    for label in data.get('labels', []):
        if label.get('name') == label_name:
            return label.get('value')
    return None

def aggregate_allure():
    all_results = []
    
    results_dirs = []
    for root, dirs, files in os.walk('.'):
        if 'allure-results' in dirs:
            results_dirs.append(os.path.join(root, 'allure-results'))
            
    if not results_dirs:
        print("No allure-results directories found.")
        return

    # Tree for documentation: epic -> feature -> story -> [tests]
    doc_tree = {}

    for results_dir in results_dirs:
        rel_path = os.path.relpath(results_dir, '.')
        module_name = rel_path
        for suffix in ['/target/allure-results', '/allure-results']:
            if module_name.endswith(suffix):
                module_name = module_name[:-len(suffix)]
                break
        
        if not module_name:
            module_name = "root"

        stats = defaultdict(int)
        issues = []
        
        result_files = glob.glob(os.path.join(results_dir, '*-result.json'))
        for result_file in result_files:
            try:
                with open(result_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    status = data.get('status', 'unknown')
                    stats[status] += 1
                    stats['total'] += 1
                    
                    start = data.get('start', 0)
                    stop = data.get('stop', 0)
                    duration = (stop - start) / 1000.0
                    stats['duration'] += duration
                    
                    if status != 'passed':
                        issues.append({
                            'name': data.get('name', 'Unknown Test'),
                            'status': status,
                            'message': data.get('statusDetails', {}).get('message', 'No message'),
                            'fullName': data.get('fullName', '')
                        })
                    else:
                        # Add to documentation tree
                        epic = get_label(data, 'epic') or "Other"
                        feature = get_label(data, 'feature') or "General"
                        story = get_label(data, 'story') or ""
                        
                        if epic not in doc_tree: doc_tree[epic] = {}
                        if feature not in doc_tree[epic]: doc_tree[epic][feature] = {}
                        if story not in doc_tree[epic][feature]: doc_tree[epic][feature][story] = []
                        
                        params = []
                        for p in data.get('parameters', []):
                            if p.get('mode') != 'hidden':
                                params.append(f"{p.get('name')}: `{p.get('value')}`")
                        
                        doc_tree[epic][feature][story].append({
                            'name': data.get('name', 'Unknown Test'),
                            'duration': duration,
                            'params': params,
                            'module': module_name
                        })

            except Exception as e:
                print(f"Error reading {result_file}: {e}")
        
        if stats['total'] > 0:
            all_results.append({
                'module': module_name,
                'path': rel_path,
                'stats': stats,
                'issues': issues
            })
    
    if not all_results:
        print("No test results found.")
        return

    # Generate Markdown
    md = "# Collective Allure Test Report & Documentation\n\n"
    md += f"Generated on: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n"
    
    md += "## 📊 Execution Summary\n\n"
    md += "| Module | Total | Passed | Failed | Broken | Skipped | Duration |\n"
    md += "| :--- | :---: | :---: | :---: | :---: | :---: | :---: |\n"
    
    grand_total_stats = defaultdict(int)
    for res in sorted(all_results, key=lambda x: x['module']):
        s = res['stats']
        md += f"| {res['module']} | {s['total']} | {s['passed']} | {s['failed']} | {s['broken']} | {s['skipped']} | {format_duration(s['duration'])} |\n"
        for k, v in s.items():
            if isinstance(v, (int, float)) and k != 'total' and k != 'duration':
                grand_total_stats[k] += v
            elif k == 'total':
                grand_total_stats['total'] += v
            elif k == 'duration':
                grand_total_stats['duration'] += v

    md += f"| **TOTAL** | **{grand_total_stats['total']}** | **{grand_total_stats['passed']}** | **{grand_total_stats['failed']}** | **{grand_total_stats['broken']}** | **{grand_total_stats['skipped']}** | **{format_duration(grand_total_stats['duration'])}** |\n\n"
    
    # Documentation Section
    md += "## 📝 Test Documentation (Behaviors)\n\n"
    md += "This section describes the verified system behaviors based on passing tests.\n\n"
    
    for epic in sorted(doc_tree.keys()):
        md += f"### Epic: {epic}\n\n"
        for feature in sorted(doc_tree[epic].keys()):
            md += f"#### Feature: {feature}\n\n"
            for story, tests in sorted(doc_tree[epic][feature].items()):
                if story:
                    md += f"##### Story: {story}\n\n"
                
                for test in sorted(tests, key=lambda x: x['name']):
                    md += f"- **{test['name']}**\n"
                    if test['params']:
                        md += "  - *Parameters:*\n"
                        for param in test['params']:
                            md += f"    - {param}\n"
                md += "\n"

    # Issues Section
    has_issues = any(len(res['issues']) > 0 for res in all_results)
    if has_issues:
        md += "## ⚠️ Issues (Failed, Broken, or Skipped)\n\n"
        for res in sorted(all_results, key=lambda x: x['module']):
            if res['issues']:
                md += f"### Module: {res['module']}\n\n"
                for test in res['issues']:
                    status_emoji = "❌" if test['status'] == 'failed' else "🔥" if test['status'] == 'broken' else "⏭️"
                    md += f"#### {status_emoji} {test['name']} ({test['status']})\n"
                    if test['fullName']:
                        md += f"**Full Name:** `{test['fullName']}`\n\n"
                    md += f"**Message:**\n```\n{test['message']}\n```\n\n"
    
    report_file = 'allure-summary.md'
    with open(report_file, 'w', encoding='utf-8') as f:
        f.write(md)
    
    print(f"Report successfully generated: {report_file}")

if __name__ == "__main__":
    aggregate_allure()
